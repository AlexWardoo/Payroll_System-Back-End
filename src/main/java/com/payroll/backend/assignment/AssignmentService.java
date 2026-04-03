package com.payroll.backend.assignment;

import com.payroll.backend.merchant.Merchant;
import com.payroll.backend.merchant.MerchantRepository;
import com.payroll.backend.user.User;
import com.payroll.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.http.HttpStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.math.BigDecimal;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;

    public List<Assignment> getAssignmentsForMerchant(String merchantId) {
        return assignmentRepository.findByMerchantMerchantIdAndActiveTrue(merchantId);
    }

    public List<Assignment> getAssignmentsForUser(Long userId) {
        return assignmentRepository.findByUserIdAndActiveTrue(userId);
    }

    public List<Assignment> getAssignmentsForUserInMonth(Long userId, Long monthId) {
        return assignmentRepository.findByUserIdAndMonthId(userId, monthId);
    }

    public Assignment assignUserToMerchant(String merchantId, Long userId, BigDecimal percentage) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found: " + merchantId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        return assignmentRepository.findByMerchantMerchantIdAndUserIdAndActiveTrue(merchantId, userId)
                .map(existing -> {
                    existing.setPercentage(percentage);
                    existing.setBasisType(PayoutBasis.MERCHANT_NET);
                    existing.setSourceUser(null);
                    existing.setActive(true);
                    return assignmentRepository.save(existing);
                })
                .orElseGet(() -> {
                    Assignment assignment = new Assignment();
                    assignment.setMerchant(merchant);
                    assignment.setUser(user);
                    assignment.setPercentage(percentage);
                    assignment.setBasisType(PayoutBasis.MERCHANT_NET);
                    assignment.setActive(true);
                    return assignmentRepository.save(assignment);
                });
    }

    @Transactional
    public List<Assignment> replaceAssignmentsForMerchant(String merchantId, List<AssignmentRequest> requests) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found: " + merchantId));

        List<Assignment> existingAssignments = assignmentRepository.findByMerchantMerchantIdAndActiveTrueOrderByIdAsc(merchantId);
        assignmentRepository.deleteAll(existingAssignments);

        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        Set<String> seenRules = new HashSet<>();
        for (AssignmentRequest request : requests) {
            if (request.getUserId() == null || request.getPercentage() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignments require a user and percentage");
            }
            if (request.getPercentage().doubleValue() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentages cannot be negative");
            }
            if (request.getPercentage().doubleValue() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentages cannot exceed 100");
            }

            PayoutBasis basis = request.getBasisType() == null
                    ? PayoutBasis.MERCHANT_NET
                    : request.getBasisType();

            String ruleKey = request.getUserId() + ":" + basis + ":" + Objects.toString(request.getSourceUserId(), "");
            if (!seenRules.add(ruleKey)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate active assignment rules are not allowed");
            }
        }

        return requests.stream()
                .map(request -> createAssignment(merchant, request))
                .map(assignmentRepository::save)
                .toList();
    }

    public void unassignUserFromMerchant(String merchantId, Long userId) {
        List<Assignment> assignments = assignmentRepository.findByMerchantMerchantIdAndUserIdAndActiveTrueOrderByIdAsc(merchantId, userId);
        if (assignments.isEmpty()) {
            throw new RuntimeException("Assignment not found for merchant " + merchantId + " and user " + userId);
        }
        if (assignments.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Multiple assignments exist for this merchant and user. Delete a specific rule instead."
            );
        }

        assignmentRepository.delete(assignments.getFirst());
    }

    public void unassignSpecificRuleFromMerchant(
            String merchantId,
            Long userId,
            PayoutBasis basisType,
            Long sourceUserId
    ) {
        Assignment assignment = assignmentRepository
                .findByMerchantMerchantIdAndUserIdAndBasisTypeAndSourceUserIdAndActiveTrue(
                        merchantId,
                        userId,
                        basisType,
                        sourceUserId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Assignment rule not found for merchant " + merchantId + " and user " + userId
                ));

        assignmentRepository.delete(assignment);
    }

    private Assignment createAssignment(Merchant merchant, AssignmentRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found: " + request.getUserId()
                ));
    
        Assignment assignment = new Assignment();
        assignment.setMerchant(merchant);
        assignment.setUser(user);
    
        BigDecimal percentage = request.getPercentage();
        assignment.setPercentage(percentage);
        assignment.setActive(true);
    
        PayoutBasis basis = request.getBasisType() == null
                ? PayoutBasis.MERCHANT_NET
                : request.getBasisType();
        assignment.setBasisType(basis);
    
        if (basis == PayoutBasis.AGENT_NET_OVERRIDE) {
            if (request.getSourceUserId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Agent-net-override assignments require a source user"
                );
            }
    
            if (request.getSourceUserId().equals(request.getUserId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Assignments cannot reference the same user as their source"
                );
            }
    
            User sourceUser = userRepository.findById(request.getSourceUserId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Source user not found: " + request.getSourceUserId()
                    ));
    
            assignment.setSourceUser(sourceUser);
        } else {
            assignment.setSourceUser(null);
        }
    
        return assignment;
    }
}

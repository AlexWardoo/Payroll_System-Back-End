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

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;

    public List<Assignment> getAssignmentsForMerchant(Long merchantId) {
        return assignmentRepository.findByMerchantId(merchantId);
    }

    public List<Assignment> getAssignmentsForUser(Long userId) {
        return assignmentRepository.findByUserId(userId);
    }

    public List<Assignment> getAssignmentsForUserInBatch(Long userId, Long batchId) {
        return assignmentRepository.findByUserIdAndMerchantBatchId(userId, batchId);
    }

    public Assignment assignUserToMerchant(Long merchantId, Long userId, Double percentage) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Merchant not found: " + merchantId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        return assignmentRepository.findByMerchantIdAndUserId(merchantId, userId)
                .map(existing -> {
                    existing.setPercentage(percentage);
                    existing.setBasisType(PayoutBasis.MERCHANT_NET);
                    existing.setSourceUser(null);
                    return assignmentRepository.save(existing);
                })
                .orElseGet(() -> {
                    Assignment assignment = new Assignment();
                    assignment.setMerchant(merchant);
                    assignment.setUser(user);
                    assignment.setPercentage(percentage);
                    assignment.setBasisType(PayoutBasis.MERCHANT_NET);
                    return assignmentRepository.save(assignment);
                });
    }

    @Transactional
    public List<Assignment> replaceAssignmentsForMerchant(Long merchantId, List<AssignmentRequest> requests) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found: " + merchantId));

        List<Assignment> existingAssignments = assignmentRepository.findByMerchantIdOrderByIdAsc(merchantId);
        assignmentRepository.deleteAll(existingAssignments);

        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        Set<Long> seenUsers = new HashSet<>();
        for (AssignmentRequest request : requests) {
            if (request.getUserId() == null || request.getPercentage() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignments require a user and percentage");
            }
            if (!seenUsers.add(request.getUserId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate users are not allowed on a merchant");
            }
            if (request.getPercentage() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentages cannot be negative");
            }
        }

        return requests.stream()
                .map(request -> createAssignment(merchant, request))
                .map(assignmentRepository::save)
                .toList();
    }

    public void unassignUserFromMerchant(Long merchantId, Long userId) {
        Assignment assignment = assignmentRepository.findByMerchantIdAndUserId(merchantId, userId)
                .orElseThrow(() -> new RuntimeException(
                        "Assignment not found for merchant " + merchantId + " and user " + userId
                ));

        assignmentRepository.delete(assignment);
    }

    private Assignment createAssignment(Merchant merchant, AssignmentRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + request.getUserId()));

        Assignment assignment = new Assignment();
        assignment.setMerchant(merchant);
        assignment.setUser(user);
        assignment.setPercentage(request.getPercentage());

        PayoutBasis basis = request.getBasisType() == null ? PayoutBasis.MERCHANT_NET : request.getBasisType();
        assignment.setBasisType(basis);

        if (basis == PayoutBasis.AGENT_PAYOUT) {
            if (request.getSourceUserId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Override assignments require a source user");
            }
            if (request.getSourceUserId().equals(request.getUserId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Override assignments cannot reference themselves");
            }

            User sourceUser = userRepository.findById(request.getSourceUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source user not found: " + request.getSourceUserId()));
            assignment.setSourceUser(sourceUser);
        }

        return assignment;
    }
}

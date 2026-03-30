package com.payroll.backend.assignment;

import com.payroll.backend.merchant.Merchant;
import com.payroll.backend.merchant.MerchantRepository;
import com.payroll.backend.user.User;
import com.payroll.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
                    return assignmentRepository.save(existing);
                })
                .orElseGet(() -> {
                    Assignment assignment = new Assignment();
                    assignment.setMerchant(merchant);
                    assignment.setUser(user);
                    assignment.setPercentage(percentage);
                    return assignmentRepository.save(assignment);
                });
    }

    public void unassignUserFromMerchant(Long merchantId, Long userId) {
        Assignment assignment = assignmentRepository.findByMerchantIdAndUserId(merchantId, userId)
                .orElseThrow(() -> new RuntimeException(
                        "Assignment not found for merchant " + merchantId + " and user " + userId
                ));

        assignmentRepository.delete(assignment);
    }
}
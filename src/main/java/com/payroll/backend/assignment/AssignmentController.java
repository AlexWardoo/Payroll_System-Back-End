package com.payroll.backend.assignment;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @GetMapping("/merchant/{merchantId}")
    public List<Assignment> getAssignmentsForMerchant(@PathVariable Long merchantId) {
        return assignmentService.getAssignmentsForMerchant(merchantId);
    }

    @GetMapping("/user/{userId}")
    public List<Assignment> getAssignmentsForUser(@PathVariable Long userId) {
        return assignmentService.getAssignmentsForUser(userId);
    }

    @GetMapping("/user/{userId}/batch/{batchId}")
    public List<Assignment> getAssignmentsForUserInBatch(
            @PathVariable Long userId,
            @PathVariable Long batchId
    ) {
        return assignmentService.getAssignmentsForUserInBatch(userId, batchId);
    }

    @PutMapping("/merchant/{merchantId}")
    public List<Assignment> replaceAssignmentsForMerchant(
            @PathVariable Long merchantId,
            @RequestBody MerchantAssignmentsUpdateRequest request
    ) {
        return assignmentService.replaceAssignmentsForMerchant(merchantId, request.getAssignments());
    }

    @DeleteMapping
    public void unassignUserFromMerchant(
            @RequestParam Long merchantId,
            @RequestParam Long userId
    ) {
        assignmentService.unassignUserFromMerchant(merchantId, userId);
    }
}

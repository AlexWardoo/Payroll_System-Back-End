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
    public List<Assignment> getAssignmentsForMerchant(@PathVariable String merchantId) {
        return assignmentService.getAssignmentsForMerchant(merchantId);
    }

    @GetMapping("/user/{userId}")
    public List<Assignment> getAssignmentsForUser(@PathVariable Long userId) {
        return assignmentService.getAssignmentsForUser(userId);
    }

    @GetMapping("/user/{userId}/month/{monthId}")
    public List<Assignment> getAssignmentsForUserInMonth(
            @PathVariable Long userId,
            @PathVariable Long monthId
    ) {
        return assignmentService.getAssignmentsForUserInMonth(userId, monthId);
    }

    @PutMapping("/merchant/{merchantId}")
    public List<Assignment> replaceAssignmentsForMerchant(
            @PathVariable String merchantId,
            @RequestBody MerchantAssignmentsUpdateRequest request
    ) {
        return assignmentService.replaceAssignmentsForMerchant(merchantId, request.getAssignments());
    }

    @DeleteMapping
    public void unassignUserFromMerchant(
            @RequestParam String merchantId,
            @RequestParam Long userId,
            @RequestParam(required = false) PayoutBasis basisType,
            @RequestParam(required = false) Long sourceUserId
    ) {
        if (basisType != null) {
            assignmentService.unassignSpecificRuleFromMerchant(merchantId, userId, basisType, sourceUserId);
            return;
        }

        assignmentService.unassignUserFromMerchant(merchantId, userId);
    }
}

package com.payroll.backend.user;

import com.payroll.backend.auth.AuthUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/employees")
    public List<AuthUserResponse> getEmployees() {
        return userRepository.findByRole(UserRole.EMPLOYEE).stream()
                .sorted(Comparator.comparing(
                        User::getDisplayName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                ))
                .map(AuthUserResponse::from)
                .toList();
    }
}
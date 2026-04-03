package com.payroll.backend.merchant;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @GetMapping
    public List<Merchant> getMerchants(@RequestParam Long monthId) {
        return merchantService.getMerchantsForMonth(monthId);
    }

    @GetMapping("/{id}")
    public Merchant getMerchant(@PathVariable String id) {
        return merchantService.getMerchant(id);
    }

}

package com.payroll.backend;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashGenerator {

    public static void main(String[] args) {

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String alexPassword = encoder.encode("Adog041401");
        String victorPassword = encoder.encode("victordemo123");

        System.out.println("Alex hash:");
        System.out.println(alexPassword);

        System.out.println("\nVictor hash:");
        System.out.println(victorPassword);
    }
}
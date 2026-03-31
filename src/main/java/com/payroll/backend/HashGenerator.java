package com.payroll.backend;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class HashGenerator {

    public static void main(String[] args) {

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String SimeonPassword = encoder.encode("JewishMafia2363");
        String CeasarPassword = encoder.encode("LionsMedia4767");
        String victorPassword = encoder.encode("Beaner5784");

        System.out.println("\nSimeon hash:");
        System.out.println(SimeonPassword);

        System.out.println("\nCeasar hash:");
        System.out.println(CeasarPassword);

        System.out.println("\nVictor hash:");
        System.out.println(victorPassword);
    }
}
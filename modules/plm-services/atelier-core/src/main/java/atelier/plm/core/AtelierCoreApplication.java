// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Atelier core service: owns the part tags Atelier keeps about the PLMs' parts. Not a PLM, but described
 * like one: the component scan covers atelier.plm so the shared catalogue, mapping and tables
 * endpoints are registered under /core.
 */
@SpringBootApplication(scanBasePackages = "atelier.plm")
public class AtelierCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtelierCoreApplication.class, args);
    }
}

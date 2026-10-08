// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.de;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** German PLM service. Component scan covers atelier.plm so the shared catalogue endpoint is registered. */
@SpringBootApplication(scanBasePackages = "atelier.plm")
public class PlmDeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlmDeApplication.class, args);
    }
}

// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.uk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** British PLM service. Component scan covers atelier.plm so the shared catalogue endpoint is registered. */
@SpringBootApplication(scanBasePackages = "atelier.plm")
public class PlmUkApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlmUkApplication.class, args);
    }
}

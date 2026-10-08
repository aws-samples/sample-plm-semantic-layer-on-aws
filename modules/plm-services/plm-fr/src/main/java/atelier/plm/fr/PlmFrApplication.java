// Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: MIT-0

package atelier.plm.fr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** French PLM service. Component scan covers atelier.plm so the shared catalogue endpoint is registered. */
@SpringBootApplication(scanBasePackages = "atelier.plm")
public class PlmFrApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlmFrApplication.class, args);
    }
}

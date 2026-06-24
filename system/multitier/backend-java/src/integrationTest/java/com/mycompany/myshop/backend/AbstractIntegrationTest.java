package com.mycompany.myshop.backend;

import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {
}

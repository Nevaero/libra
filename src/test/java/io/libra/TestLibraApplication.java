package io.libra;

import org.springframework.boot.SpringApplication;

public class TestLibraApplication {

    public static void main(String[] args) {
        SpringApplication.from(LibraApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}

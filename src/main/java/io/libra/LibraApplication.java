package io.libra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class LibraApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraApplication.class, args);
    }

}

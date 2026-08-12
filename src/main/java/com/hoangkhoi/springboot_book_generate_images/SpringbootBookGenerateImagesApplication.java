package com.hoangkhoi.springboot_book_generate_images;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class SpringbootBookGenerateImagesApplication {

    @GetMapping("/hello")
    String sayHello() {
        return "Hello Spring Boot, Khoi!";
    }

	public static void main(String[] args) {
		SpringApplication.run(SpringbootBookGenerateImagesApplication.class, args);
	}

}

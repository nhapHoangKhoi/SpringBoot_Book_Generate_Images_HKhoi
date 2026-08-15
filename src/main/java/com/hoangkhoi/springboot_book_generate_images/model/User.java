package com.hoangkhoi.springboot_book_generate_images.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;


public class User {

    private String id;
    private String name;
    private String email;

    public User() {
    }

    public static User of(String name, String email) {
        User u = new User();
        u.email = normaliseEmail(email);
        u.id = idFor(u.email);
        u.name = name;
        return u;
    }

    // case and surrounding space are not significant
    public static String idFor(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normaliseEmail(email).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    private static String normaliseEmail(String email) {
        return email.trim().toLowerCase();
    }

    /* Getter, setter */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    /* End getter, setter */
}

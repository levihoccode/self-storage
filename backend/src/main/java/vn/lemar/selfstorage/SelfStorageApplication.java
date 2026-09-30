package vn.lemar.selfstorage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Äiá»ƒm khá»Ÿi Ä‘á»™ng cá»§a há»‡ thá»‘ng self-storage.
 *
 * <p>Kiáº¿n trÃºc modular monolith theo issue #15: má»—i flow nghiá»‡p vá»¥ lÃ  má»™t package con
 * trá»±c tiáº¿p cá»§a package nÃ y vÃ  Ä‘Æ°á»£c Spring Modulith coi lÃ  má»™t module Ä‘á»™c láº­p.
 */
@SpringBootApplication
public class SelfStorageApplication {

    public static void main(String[] args) {
        SpringApplication.run(SelfStorageApplication.class, args);
    }
}

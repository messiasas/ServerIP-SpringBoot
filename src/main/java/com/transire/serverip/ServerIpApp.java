package com.transire.serverip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication // this is "annotation", a method to insert metadatas/behavior into a class
public class ServerIpApp {
    public static void main(String[] args){
        SpringApplication.run(com.transire.serverip.ServerIpApp.class, args);
    }
}
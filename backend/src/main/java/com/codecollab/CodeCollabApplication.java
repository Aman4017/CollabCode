package com.codecollab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CodeCollabApplication — The main entry point of the Spring Boot backend.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * This is the starting point of the entire Java backend server. When you run
 * the application, Java begins execution right here in the main() method.
 *
 * HOW IT WORKS:
 * -------------
 * 1. @SpringBootApplication is a powerful annotation that combines three things:
 *    - @Configuration:     Tells Spring this class can define "beans" (objects Spring manages).
 *    - @EnableAutoConfiguration: Tells Spring Boot to automatically configure the application
 *                           based on the dependencies in pom.xml (e.g., it sees "spring-boot-starter-web"
 *                           and automatically sets up an embedded web server).
 *    - @ComponentScan:     Tells Spring to scan the "com.codecollab" package (and sub-packages)
 *                           for classes annotated with @Component, @Service, @Controller, etc.,
 *                           and automatically create instances of them.
 *
 * 2. SpringApplication.run() boots up the entire application:
 *    - Starts an embedded Tomcat web server (listens on port 8080 by default).
 *    - Scans for and initializes all components (RoomService, CompilerService, etc.).
 *    - Registers WebSocket endpoints and REST API routes.
 *    - Once started, the server waits for incoming HTTP and WebSocket connections.
 *
 * WHY THIS CLASS IS NEEDED:
 * -------------------------
 * Every Spring Boot application needs exactly one class with @SpringBootApplication
 * and a main() method. Without it, the server cannot start.
 */
@SpringBootApplication
public class CodeCollabApplication {

    /**
     * The main method — Java's universal entry point for any application.
     *
     * When you run the command `mvn spring-boot:run` or execute the JAR file,
     * Java calls this method first. It delegates to Spring Boot's
     * SpringApplication.run() which handles all the heavy lifting of starting
     * the web server, initializing components, and making the app ready to
     * accept connections.
     *
     * @param args Command-line arguments passed when starting the application.
     *             For example: `java -jar app.jar --server.port=9090` would
     *             override the default port. Spring Boot automatically parses
     *             these arguments.
     */
    public static void main(String[] args) {
        SpringApplication.run(CodeCollabApplication.class, args);
    }
}

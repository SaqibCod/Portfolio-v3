/**
 * REST endpoints only: routing, {@code @Valid} request DTOs, status codes and headers.
 * No business logic, and never a call to a repository or an external client. Controllers
 * depend on services and exchange DTOs, never model classes.
 */
package dev.saq.portfolio.controller;

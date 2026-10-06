package com.example.leavemanagement.controller;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.dto.LeaveRequestDto;
import com.example.leavemanagement.service.LeaveRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Thin HTTP layer: binding, validation trigger and status codes only.
 * Business rules -> LeaveRequestService, error mapping -> GlobalExceptionHandler.
 */
@RestController
@RequestMapping("/api/leave-requests")
public class LeaveRequestsController {

    private final LeaveRequestService service;

    public LeaveRequestsController(LeaveRequestService service) {
        this.service = service;
    }

    @GetMapping
    public List<LeaveRequestDto> getAll() {
        return service.findAll();
    }

    // Constraints on @RequestParam use Spring 6.1 built-in method validation -> 400 via
    // HandlerMethodValidationException (deliberately no class-level @Validated, which would
    // switch to the AOP path and throw ConstraintViolationException instead).
    @GetMapping("/search")
    public List<LeaveRequestDto> search(@RequestParam @NotBlank @Size(max = 100) String name) {
        return service.searchByEmployeeName(name);
    }

    @PostMapping
    public ResponseEntity<LeaveRequestDto> create(@Valid @RequestBody CreateLeaveRequestDto dto) {
        LeaveRequestDto created = service.create(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }
}

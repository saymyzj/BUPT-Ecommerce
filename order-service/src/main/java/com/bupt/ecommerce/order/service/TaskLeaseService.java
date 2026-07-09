package com.bupt.ecommerce.order.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TaskLeaseService {

    private final JdbcTemplate jdbcTemplate;
    private final String ownerId = UUID.randomUUID().toString();

    public TaskLeaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean tryAcquire(String taskName, Duration duration) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plus(duration);
        jdbcTemplate.update("""
                INSERT INTO scheduled_task_locks(task_name, locked_by, locked_until, updated_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  locked_by = IF(locked_until < ?, VALUES(locked_by), locked_by),
                  locked_until = IF(locked_until < ?, VALUES(locked_until), locked_until),
                  updated_at = IF(locked_by = VALUES(locked_by), VALUES(updated_at), updated_at)
                """, taskName, ownerId, until, now, now, now);
        String owner = jdbcTemplate.queryForObject(
                "SELECT locked_by FROM scheduled_task_locks WHERE task_name = ?",
                String.class,
                taskName
        );
        return ownerId.equals(owner);
    }

    public void release(String taskName) {
        jdbcTemplate.update(
                "UPDATE scheduled_task_locks SET locked_until = ?, updated_at = ? WHERE task_name = ? AND locked_by = ?",
                LocalDateTime.now(),
                LocalDateTime.now(),
                taskName,
                ownerId
        );
    }
}

package com.shelter.core.service;

import com.shelter.core.dto.EmployeeAdminResponse;
import com.shelter.core.dto.EmployeeBrief;
import com.shelter.core.exception.EmployeeNotLinkedException;
import com.shelter.core.repository.EmployeeRepository;
import com.shelter.core.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final CurrentUser currentUser;

    public EmployeeService(EmployeeRepository employeeRepository, CurrentUser currentUser) {
        this.employeeRepository = employeeRepository;
        this.currentUser = currentUser;
    }

    /** Админ-панель: все сотрудники с должностями и окладами. */
    public List<EmployeeAdminResponse> findAllForAdmin() {
        return employeeRepository.findAllForAdmin();
    }

    /** Что о сотруднике видят остальные роли: ФИО и должность. */
    public Optional<EmployeeBrief> findBrief(Long employeeId) {
        return employeeRepository.findBrief(employeeId);
    }

    /**
     * Карточка сотрудника текущего пользователя. Нужна действиям, которые записывают employee_id
     * (процедуры, расход, списание — шаги 4–6). Без карточки — 409 с понятным сообщением.
     */
    public Long currentEmployeeId() {
        Long userId = currentUser.id();
        if (userId == null) {
            throw new EmployeeNotLinkedException();
        }
        return employeeRepository.findActiveIdByUserId(userId).orElseThrow(EmployeeNotLinkedException::new);
    }
}

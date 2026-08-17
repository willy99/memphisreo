package com.memphisreo.security.jwt;

import com.memphisreo.security.PlatformStaff;
import com.memphisreo.security.PlatformStaffRepository;

import java.util.UUID;

public class PlatformStaffTokenRevocationCheck implements TokenRevocationCheck {

    private final PlatformStaffRepository repository;

    public PlatformStaffTokenRevocationCheck(PlatformStaffRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isStillValid(UUID accountId, int tokenVersionInToken) {
        return repository.findById(accountId)
                .map(staff -> staff.getStatus() == PlatformStaff.Status.ACTIVE
                        && staff.getTokenVersion() == tokenVersionInToken)
                .orElse(false);
    }
}

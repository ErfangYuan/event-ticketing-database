package mytix.database.user;

import java.time.LocalDate;

public record UserRecord(
        int userID,
        String email,
        String passwordHash,
        String name,
        String address,
        LocalDate birthday,
        String userType,
        String creditCardNumber) {}

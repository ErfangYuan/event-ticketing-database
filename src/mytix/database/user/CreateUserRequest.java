package mytix.database.user;

import java.time.LocalDate;

public record CreateUserRequest(
        String email,
        String plainPassword,
        String name,
        String address,
        LocalDate birthday,
        String userType,
        String cardNumber,
        String cardExpiry) {}

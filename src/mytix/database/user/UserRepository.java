package mytix.database.user;

import java.util.Optional;

public interface UserRepository {

    Optional<UserRecord> findByEmail(String email);

    
    Optional<UserRecord> authenticate(String email, String password, String expectedType);

    
    int createAccount(CreateUserRequest request);

    
    boolean deleteAccount(String email, String password);
}

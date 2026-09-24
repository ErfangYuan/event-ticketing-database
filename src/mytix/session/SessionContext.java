package mytix.session;

public final class SessionContext {

    private Integer userID;
    private String email;
    private String userType; 
    private String name;

    public boolean isLoggedIn() {
        return userID != null;
    }

    public Integer getUserID() {
        return userID;
    }

    public String getEmail() {
        return email;
    }

    public String getUserType() {
        return userType;
    }

    public String getName() {
        return name;
    }

    public void login(int userID, String email, String userType, String name) {
        this.userID = userID;
        this.email = email;
        this.userType = userType;
        this.name = name;
    }

    public void logout() {
        this.userID = null;
        this.email = null;
        this.userType = null;
        this.name = null;
    }

    public boolean isCustomer() {
        return "CUSTOMER".equalsIgnoreCase(userType);
    }

    public boolean isOrganizer() {
        return "ORGANIZER".equalsIgnoreCase(userType);
    }

    @Override
    public String toString() {
        if (!isLoggedIn()) {
            return "(not logged in)";
        }
        return userType + " #" + userID + " <" + email + ">";
    }
}

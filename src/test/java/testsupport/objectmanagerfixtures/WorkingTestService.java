package testsupport.objectmanagerfixtures;

public class WorkingTestService implements TestService {
    private final String name;
    private final int version;

    public WorkingTestService(String name, int version) {
        this.name = name;
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public int getVersion() {
        return version;
    }
}

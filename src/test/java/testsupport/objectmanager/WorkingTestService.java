package testsupport.objectmanager;

public class WorkingTestService implements TestService {
    private final String name;

    public WorkingTestService(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }
}


package opendota.entity;

import java.util.ArrayList;
import java.util.List;

public class ReplayResponse {
    private List<Entry> matchEntries;
    private ErrorMessage errorMessage;

    public ReplayResponse() {
        this(new ArrayList<>(), new ErrorMessage());
    }

    public ReplayResponse(List<Entry> matchEntries, ErrorMessage errorMessage) {
        this.matchEntries = matchEntries;
        this.errorMessage = errorMessage;
    }

    public List<Entry> getMatchEntries() {
        return matchEntries;
    }

    public void setMatchEntries(List<Entry> matchEntries) {
        this.matchEntries = matchEntries;
    }

    public ErrorMessage getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(ErrorMessage errorMessage) {
        this.errorMessage = errorMessage;
    }
}

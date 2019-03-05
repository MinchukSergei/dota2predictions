package opendota.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ReplayResponse {
    private List<Entry> matchEntries;
    private ErrorMessage errorMessage;
    private HashMap<Integer, Integer> heroesOrder;

    public ReplayResponse() {
        this(new ArrayList<>(), new ErrorMessage(), new HashMap<>());
    }

    public ReplayResponse(List<Entry> matchEntries, ErrorMessage errorMessage, HashMap<Integer, Integer> heroesOrder) {
        this.matchEntries = matchEntries;
        this.errorMessage = errorMessage;
        this.heroesOrder = heroesOrder;
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

    public HashMap<Integer, Integer> getHeroesOrder() {
        return heroesOrder;
    }

    public void setHeroesOrder(HashMap<Integer, Integer> heroesOrder) {
        this.heroesOrder = heroesOrder;
    }
}

package cs451.Milestone2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import cs451.Milestone1.Pair;
import cs451.Milestone1.Host.ActiveHost;
import cs451.Milestone1.Host.HostParams;

public class Transceiver extends ActiveHost {

    List<String> correct;
    Set<Pair<String, String>> received;

    @Override
    public boolean populate(HostParams hostParams, String outputFilePath) {
        boolean result = super.populate(hostParams, outputFilePath);
        return result;
    }

    public void init() {
        correct = new ArrayList<>();
    }

    public void broadcast() {
    }


}

#!/usr/bin/env python3

# [Keep your imports as is]

class StressTest:
    def __init__(self, procs):
        self.processesInfo = {logicalPID: ProcessInfo(handle) for logicalPID, handle in procs}

    def run_for_duration(self, duration):
        start_time = time.time()
        while time.time() - start_time < duration:
            time.sleep(0.1)  # Short sleep to allow processes to run smoothly
        print(f"Stress test completed after {duration} seconds.")

    def terminate_all_processes(self):
        for _, info in self.processesInfo.items():
            with info.lock:
                if info.state != ProcessState.TERMINATED:
                    if info.state == ProcessState.STOPPED:
                        info.handle.send_signal(ProcessInfo.stateToSignal(ProcessState.RUNNING))
                    info.handle.send_signal(ProcessInfo.stateToSignal(ProcessState.TERMINATED))

def main(parser_results):
    cmd = parser_results.command
    runscript = parser_results.runscript
    logsDir = parser_results.logsDir
    processes = parser_results.processes

    if not os.path.isdir(logsDir):
        raise ValueError(f"Directory `{logsDir}` does not exist")

    if cmd == "perfect":
        validation = Validation(processes, parser_results.messages)
        hostsFile, configFile = validation.generatePerfectLinksConfig(logsDir)
        configFiles = [configFile]
    elif cmd == "fifo":
        validation = Validation(processes, parser_results.messages)
        hostsFile, configFile = validation.generateFifoConfig(logsDir)
        configFiles = [configFile]
    elif cmd == "agreement":
        proposals = parser_results.proposals
        pmv = parser_results.proposal_max_values
        pdv = parser_results.proposals_distinct_values

        if pmv > pdv:
            print("The distinct proposal values must at least as many as the maximum values per proposal")
            sys.exit(1)

        validation = LatticeAgreementValidation(processes, proposals, pmv, pdv)
        hostsFile, configFiles = validation.generate(logsDir)
    else:
        raise ValueError("Unrecognized command")

    try:
        # Start the processes
        procs = startProcesses(processes, runscript, hostsFile, configFiles, logsDir)

        for logicalPID, procHandle in procs:
            print(f"Process with logicalPID {logicalPID} has PID {procHandle.pid}")

        # Run the stress test for a fixed duration
        st = StressTest(procs)
        st.run_for_duration(10)

        print("Resuming stopped processes.")
        st.terminate_all_processes()

    finally:
        if procs is not None:
            for _, p in procs:
                p.kill()

if __name__ == "__main__":
    # [Parser setup remains the same as in your original code]
    results = parser.parse_args()
    main(results)

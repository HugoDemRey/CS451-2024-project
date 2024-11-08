#!/usr/bin/env python3

import argparse
import os, sys, atexit
import textwrap
import time
import threading, subprocess
import itertools


import signal
import random
import time
from enum import Enum

from collections import defaultdict, OrderedDict

PROCESSES_BASE_IP = 11000


def positive_int(value):
    ivalue = int(value)
    if ivalue <= 0:
        raise argparse.ArgumentTypeError("{} is not positive integer".format(value))
    return ivalue


class ProcessState(Enum):
    RUNNING = 1
    STOPPED = 2
    TERMINATED = 3


class ProcessInfo:
    def __init__(self, handle):
        self.lock = threading.Lock()
        self.handle = handle
        self.state = ProcessState.RUNNING

    @staticmethod
    def stateToSignal(state):
        if state == ProcessState.RUNNING:
            return signal.SIGCONT

        if state == ProcessState.STOPPED:
            return signal.SIGSTOP

        if state == ProcessState.TERMINATED:
            return signal.SIGTERM

    @staticmethod
    def stateToSignalStr(state):
        if state == ProcessState.RUNNING:
            return "SIGCONT"

        if state == ProcessState.STOPPED:
            return "SIGSTOP"

        if state == ProcessState.TERMINATED:
            return "SIGTERM"

    @staticmethod
    def validStateTransition(current, desired):
        if current == ProcessState.TERMINATED:
            return False

        if current == ProcessState.RUNNING:
            return desired == ProcessState.STOPPED or desired == ProcessState.TERMINATED

        if current == ProcessState.STOPPED:
            return desired == ProcessState.RUNNING

        return False


class AtomicSaturatedCounter:
    def __init__(self, saturation, initial=0):
        self._saturation = saturation
        self._value = initial
        self._lock = threading.Lock()

    def reserve(self):
        with self._lock:
            if self._value < self._saturation:
                self._value += 1
                return True
            else:
                return False


class Validation:
    def __init__(self, procs, msgs):
        self.processes = procs
        self.messages = msgs

    def generatePerfectLinksConfig(self, directory):
        hostsfile = os.path.join(directory, "hosts")
        configfile = os.path.join(directory, "config")

        with open(hostsfile, "w") as hosts:
            for i in range(1, self.processes + 1):
                hosts.write("{} localhost {}\n".format(i, PROCESSES_BASE_IP + i))

        with open(configfile, "w") as config:
            config.write("{} 1\n".format(self.messages))

        return (hostsfile, configfile)

    def generateFifoConfig(self, directory):
        hostsfile = os.path.join(directory, "hosts")
        configfile = os.path.join(directory, "config")

        with open(hostsfile, "w") as hosts:
            for i in range(1, self.processes + 1):
                hosts.write("{} localhost {}\n".format(i, PROCESSES_BASE_IP + i))

        with open(configfile, "w") as config:
            config.write("{}\n".format(self.messages))

        return (hostsfile, configfile)


class LatticeAgreementValidation:
    def __init__(self, processes, proposals, max_proposal_size, distinct_values):
        self.procs = processes
        self.props = proposals
        self.mps = max_proposal_size
        self.dval = distinct_values

    def generate(self, directory):
        hostsfile = os.path.join(directory, "hosts")

        with open(hostsfile, "w") as hosts:
            for i in range(1, self.procs + 1):
                hosts.write("{} localhost {}\n".format(i, PROCESSES_BASE_IP + i))

        maxint = 2**31 - 1
        seeded_rand = random.Random(42)
        try:
            values = seeded_rand.sample(range(0, maxint + 1), self.dval)
        except ValueError:
            print("Cannot have to many distinct values")
            sys.exit(1)

        configfiles = []
        for pid in range(1, self.procs + 1):
            configfile = os.path.join(directory, "proc{:02d}.config".format(pid))
            configfiles.append(configfile)

            with open(configfile, "w") as config:
                config.write("{} {} {}\n".format(self.props, self.mps, self.dval))

                for i in range(self.props):
                    proposal = seeded_rand.sample(
                        values, seeded_rand.randint(1, self.mps)
                    )
                    config.write(" ".join(map(str, proposal)))
                    config.write("\n")

        return (hostsfile, configfiles)


class StressTest:
    def __init__(self, procs):
        self.processesInfo = {logicalPID: ProcessInfo(handle) for logicalPID, handle in procs}

    def run_for_duration(self, duration):
        start_time = time.time()
        while time.time() - start_time < duration:
            time.sleep(0.1)  # Short sleep to allow processes to run smoothly

    def terminate_all_processes(self):
        for _, info in self.processesInfo.items():
            with info.lock:
                if info.state != ProcessState.TERMINATED:
                    if info.state == ProcessState.STOPPED:
                        info.handle.send_signal(ProcessInfo.stateToSignal(ProcessState.RUNNING))
                    info.handle.send_signal(ProcessInfo.stateToSignal(ProcessState.TERMINATED))


def startProcesses(processes, runscript, hostsFilePath, configFilePaths, outputDir):
    runscriptPath = os.path.abspath(runscript)
    if not os.path.isfile(runscriptPath):
        raise Exception("`{}` is not a file".format(runscriptPath))

    if os.path.basename(runscriptPath) != "run.sh":
        raise Exception("`{}` is not a runscript".format(runscriptPath))

    outputDirPath = os.path.abspath(outputDir)
    if not os.path.isdir(outputDirPath):
        raise Exception("`{}` is not a directory".format(outputDirPath))

    baseDir, _ = os.path.split(runscriptPath)
    bin_cpp = os.path.join(baseDir, "bin", "da_proc")
    bin_java = os.path.join(baseDir, "bin", "da_proc.jar")

    if os.path.exists(bin_cpp):
        cmd = [bin_cpp]
    elif os.path.exists(bin_java):
        cmd = ["java", "-jar", bin_java]
    else:
        raise Exception(
            "`{}` could not find a binary to execute. Make sure you build before validating".format(
                runscriptPath
            )
        )

    procs = []
    for pid, config_path in zip(
        range(1, processes + 1), itertools.cycle(configFilePaths)
    ):
        cmd_ext = [
            "--id",
            str(pid),
            "--hosts",
            hostsFilePath,
            "--output",
            os.path.join(outputDirPath, "proc{:02d}.output".format(pid)),
            config_path,
        ]

        stdoutFd = open(
            os.path.join(outputDirPath, "proc{:02d}.stdout".format(pid)), "w"
        )
        stderrFd = open(
            os.path.join(outputDirPath, "proc{:02d}.stderr".format(pid)), "w"
        )

        procs.append(
            (pid, subprocess.Popen(cmd + cmd_ext, stdout=stdoutFd, stderr=stderrFd))
        )

    return procs


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

        test_time = 10
        start_time = time.time()

        def print_progress_bar(percentage):
            bar_length = 50
            block = int(round(bar_length * percentage / 100))
            progress = "#" * block + "-" * (bar_length - block)
            print(f"\r[{progress}] {percentage:.2f} %", end="\r")


        def scheduler():
            while time.time() - start_time < test_time:
                elapsed = time.time() - start_time
                percentage = (elapsed / test_time) * 100
                print_progress_bar(percentage)
                time.sleep(test_time / 50)

        scheduler_thread = threading.Thread(target=scheduler)
        scheduler_thread.start()

        st.run_for_duration(test_time)

        end_time = time.time()

        print_progress_bar(100)

        receiverOutputPath = os.path.join(parser_results.logsDir, "proc01.output")
        with open(receiverOutputPath, "r") as receiverOutputFile:
            nbDeliveredMessages = len(receiverOutputFile.readlines())

        print("\nTime taken: {:.2f} seconds".format(end_time - start_time))
        print("Number of delivered messages:", nbDeliveredMessages)
        print("Average throughput: {:.2f} messages/second\n".format(nbDeliveredMessages / (end_time - start_time)))


        print("Resuming stopped processes.")
        st.terminate_all_processes()

    finally:
        if procs is not None:
            for _, p in procs:
                p.kill()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()

    sub_parsers = parser.add_subparsers(dest="command", help="stress a given milestone")
    sub_parsers.required = True
    parser_perfect = sub_parsers.add_parser("perfect", help="stress perfect links")
    parser_fifo = sub_parsers.add_parser("fifo", help="stress fifo broadcast")
    parser_agreement = sub_parsers.add_parser(
        "agreement", help="stress lattice agreement"
    )

    for subparser in [parser_perfect, parser_fifo, parser_agreement]:
        subparser.add_argument(
            "-r",
            "--runscript",
            required=True,
            dest="runscript",
            help="Path to run.sh",
        )

        subparser.add_argument(
            "-l",
            "--logs",
            required=True,
            dest="logsDir",
            help="Directory to store stdout, stderr and outputs generated by the processes",
        )

        subparser.add_argument(
            "-p",
            "--processes",
            required=True,
            type=positive_int,
            dest="processes",
            help="Number of processes that broadcast",
        )

    for subparser in [parser_perfect, parser_fifo]:
        subparser.add_argument(
            "-m",
            "--messages",
            required=True,
            type=positive_int,
            dest="messages",
            help="Maximum number (because it can crash) of messages that each process can broadcast",
        )

    parser_agreement.add_argument(
        "-n",
        "--proposals",
        required=True,
        type=positive_int,
        dest="proposals",
        help="Maximum number (because it can crash) of proposal that each process can make",
    )

    parser_agreement.add_argument(
        "-v",
        "--proposal-values",
        required=True,
        type=positive_int,
        dest="proposal_max_values",
        help="Maximum size of the proposal set that each process proposes",
    )

    parser_agreement.add_argument(
        "-d",
        "--distinct-values",
        required=True,
        type=positive_int,
        dest="proposals_distinct_values",
        help="The number of distinct values among all proposals",
    )

    results = parser.parse_args()

    main(results)

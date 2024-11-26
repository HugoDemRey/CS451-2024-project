import os
from collections import defaultdict
from tqdm import tqdm

def parse_logs(output_dir):
    """
    Parse logs from the output directory and return sent and delivered messages.
    """
    broadcast = defaultdict(list)        # sender_id -> [(receiver_id, seq_num)]
    delivered = defaultdict(list)   # receiver_id -> [(sender_id, seq_num)]
    
    # Iterate over output files in the directory
    for filename in os.listdir(output_dir):
        if filename.endswith(".output"):
            process_id = int(filename.split('.')[0])
            file_path = os.path.join(output_dir, filename)
            
            with open(file_path, 'r') as f:
                for line in f:
                    tokens = line.strip().split()
                    
                    if tokens[0] == 'b':  # Message broadcasted (sent)
                        seq_num = int(tokens[1])
                        broadcast[process_id].append((None, seq_num))  # Keep track of all sent messages
                
                    elif tokens[0] == 'd':  # Message delivered
                        sender_id = int(tokens[1])
                        seq_num = int(tokens[2])
                        delivered[process_id].append((sender_id, seq_num))  # Record delivery

    return broadcast, delivered

def check_creations(broadcast, delivered):
    """
    Check for PL3: No Creation.
    """
    violations = []
    
    for receiver_id, delivered_messages in delivered.items():
        for sender_id, seq_num in tqdm(delivered_messages, desc=f"       process {receiver_id}"):
            if (None, seq_num) not in broadcast.get(sender_id, []):
                violations.append(f"PL3 Violation: Message {seq_num} delivered by process {receiver_id} was never broadcast by process {sender_id}.")
        
        # Clear the progress bar
        tqdm.write("\033[K", end='\r')
        if len(violations) == 0:
            print(f"    🌱 No violation found for process {receiver_id}")
        else:
            for violation in violations:
                print(f"    ⛔ {violation}")
            violations = []
def count(parent_dir):

    # delivered = {}
    # broadcast = {}

    # for root, _, files in os.walk(parent_dir):
    #     number_of_d = len([file for file in files if file.endswith(".output")])


    #     for file in files:
    #         if file.endswith(".output"):
    #             with open(os.path.join(root, file), 'r') as f:

    #                 content = f.read()

    #                 delivered[file] = []
    #                 broadcast[file] = []

    #                 for line in content.splitlines():
    #                     if line.startswith("b "):
    #                         broadcast[file].append(line)
    #                     elif line.startswith("d "):
    #                         delivered[file].append(line)


    broadcast, delivered = parse_logs(parent_dir)

    print(broadcast.keys())

    # Volontary Add a duplicate in broadcast for 1.output
    broadcast[1].append(broadcast[1][0])
    
    for keys in broadcast.keys():
        print(f"\n{keys} -> ", end="")
        print(f"[{len(broadcast[keys])} b, {len(delivered[keys])} d]")

    print("\nChecking for duplicate broadcasts:")

    for host in broadcast.keys():
        setb = set(broadcast[host])
        listb = list(broadcast[host])
        diff = len(listb) - len(setb)
        if diff > 0:
            print(f"    ⛔ {diff} duplicate(s) broadcasts found for {host}!")
        else :
            print(f"    🌱 No duplicate broadcasts found for {host}")

    print("\nChecking for duplicates delivered:")
    for host in delivered.keys():
        setb = set(delivered[host])
        listb = list(delivered[host])
        diff = len(listb) - len(setb)
        if diff > 0:
            print(f"    ⛔ {diff} duplicate(s) delivered found for {host}!")
        else :
            print(f"    🌱 No duplicate delivered for {host}")

    print("\nChecking for creations:")
    check_creations(broadcast, delivered)

    # for hostA in broadcast.keys():
    #     for hostB in broadcast.keys():
    #         None

count('./../example/output/')



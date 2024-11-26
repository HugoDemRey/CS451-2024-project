import os
from collections import defaultdict
from tqdm import tqdm
from count import count

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
        for sender_id, seq_num in tqdm(delivered_messages, desc=f"       process {receiver_id}", leave=False):
            if (None, seq_num) not in broadcast.get(sender_id, []):
                violations.append(f"Violation: Message {seq_num} delivered by process {receiver_id} was never broadcast by process {sender_id}.")
        
        if len(violations) == 0:
            print(f"    🌱 No creation found for process {receiver_id}")
        else:
            for violation in violations:
                print(f"    ⛔ {violation}")
            violations = []

def check_correctness(parent_dir):

    count(parent_dir)

    broadcast, delivered = parse_logs(parent_dir)

    # Volontary Add a wrong data
    add_wrong_data: bool = False
    if (add_wrong_data):
        print("🚨🚨🚨 WARNING: Manually adding a wrong data 🚨🚨🚨")
        broadcast[1].append(broadcast[1][0])
        delivered[3].append(delivered[3][0])

    print("\nChecking for duplicate broadcasts:")

    for host in broadcast.keys():
        setb = set(broadcast[host])
        listb = list(broadcast[host])
        diff = len(listb) - len(setb)
        if diff > 0:
            print(f"    ⛔ {diff} duplicate(s) broadcasts found for {host}!")
        else :
            print(f"    🌱 No duplicate broadcasts found for {host}")

    print("\nChecking for duplicate delivered:")
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

check_correctness('./../example/output/')



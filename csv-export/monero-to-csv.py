from monerorpc import MoneroRPCInterface
import csv

rpc_interface = MoneroRPCInterface()

OUTPUT_COUNTER = {}
TX_COUNTER = 0
INPUT_COUNTER = 0
MAX_BLOCK = 1288774 + 1 


def get_blocks_and_transactions():
    for height in range(MAX_BLOCK):
        print(str(height))

        # create block
        monero_block = rpc_interface.get_block(height)
        write_block(monero_block)

        # connect to previous block
        if height > 0:
            write_previous_block(height)

        # create coinbase transaction
        coinbase_id = get_new_tx_id()
        write_transaction(coinbase_id)
        write_transaction_rel(coinbase_id, height)

        # add outputs
        for vout in monero_block.coinbase.outputs:
            create_output(vout, coinbase_id)

        # create other transactions
        if len(monero_block.tx_hashes) > 0:
            monero_txs = rpc_interface.get_transactions(monero_block.tx_hashes)

            for mtx in monero_txs:
                tx_id = get_new_tx_id()

                write_transaction(tx_id, tx_hash=mtx.tx_hash, fee=mtx.fee)
                write_transaction_rel(tx_id, height)

                for vin in mtx.inputs:
                    create_input(vin, tx_id)

                for vout in mtx.outputs:
                    create_output(vout, tx_id)


def create_output(vout, tx_id):
    global OUTPUT_COUNTER
    value = vout.amount
    if value in OUTPUT_COUNTER:
        index = OUTPUT_COUNTER[value]
        OUTPUT_COUNTER[value] += 1
    else:
        index = 0
        OUTPUT_COUNTER[value] = 1
    output_id = create_output_id(value=value, index=index)

    write_output(output_id, value, index)
    write_output_rel(tx_id, output_id)


def create_input(vin, tx_id):
    global OUTPUT_COUNTER
    input_id = get_new_input_id()
    value = vin.amount
    size_anon = OUTPUT_COUNTER[value]
    mixin = len(vin.offsets) - 1
    write_input(input_id, value, mixin, size_anon)
    write_input_rel(tx_id, input_id)

    offset = 0
    references = []
    for it in vin.offsets:
        offset += it
        references.append(create_output_id(value=value, index=offset))
    write_referenced_outputs(input_id=input_id, output_ids=references)


def get_new_tx_id():
    global TX_COUNTER
    TX_COUNTER += 1
    return "t" + str(TX_COUNTER - 1)


def get_new_input_id():
    global INPUT_COUNTER
    INPUT_COUNTER += 1
    return "i" + str(INPUT_COUNTER - 1)


def create_output_id(value, index):
    return str(value) + "-" + str(index)


def write_block(block):
    with open("csv/blocks.csv", "a") as f:
        writer = csv.writer(f)
        writer.writerow([block.height, block.height, block.block_hash, block.timestamp])


def write_transaction(tx_id, tx_hash="", fee=0):
    with open("csv/transactions.csv", "a") as f:
        writer = csv.writer(f)
        writer.writerow([tx_id, tx_hash, fee])


def write_transaction_rel(tx_id, height):
    with open("csv/tx-blocks.csv", "a") as f:
        writer = csv.writer(f)
        writer.writerow([tx_id, height])


def write_previous_block(height):
    with open("csv/blocks-rels.csv", "a") as f:
        writer = csv.writer(f)
        writer.writerow([height, height - 1])


def write_output(output_id, value, index):
    with open("csv/outputs.csv", "a") as f:
        writer = csv.writer(f)
        writer.writerow([output_id, value, index])


def write_output_rel(tx_id, output_id):
    with open("csv/output-rels.csv", "a") as f:
        writer = csv.writer(f)
        writer.writerow([tx_id, output_id])


def write_input(input_id, value, mixin, size_anon):
    with open("csv/inputs.csv", "a") as f:
        writer = csv.writer(f)
        writer.writerow([input_id, value, mixin, size_anon])


def write_input_rel(tx_id, input_id):
    with open("csv/input-rels.csv", "a") as f:
        writer = csv.writer(f)
        writer.writerow([tx_id, input_id])


def write_referenced_outputs(input_id, output_ids):
    with open("csv/input-output-refs.csv", "a") as f:
        writer = csv.writer(f)
        for oid in output_ids:
            writer.writerow([input_id, oid])


if __name__ == "__main__":
    get_blocks_and_transactions()

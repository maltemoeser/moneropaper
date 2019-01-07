import json
import requests


class MoneroRPCInterface(object):
    def __init__(self, url="http://127.0.0.1:18081/"):
        self.url = url

    def _rpc_data_template(self, method, params=None):
        data = {
            "jsonrpc": "2.0",
            "id": "0",
            "method": method
        }
        if params:
            data["params"] = params
        return data

    def make_request(self, data, url_extension):
        return requests.post(self.url + url_extension, data=json.dumps(data))

    def get_blockcount(self):
        """
        :rtype: int
        :return: the number of blocks in the Monero blockchain
        """
        data = self._rpc_data_template("getblockcount")
        r = self.make_request(data, "json_rpc")
        response = r.json()
        return response["result"]["count"]

    def get_block(self, height):
        """

        :rtype: MoneroBlock
        """
        data = self._rpc_data_template("getblock", params={"height": height})
        r = self.make_request(data, "json_rpc")
        response = r.json()
        block = MoneroBlock()
        block.from_rpc(response)
        return block

    def get_block_header(self, height):
        """

        :rtype: MoneroBlock
        """
        data = {
            "jsonrpc": "2.0",
            "id": "0",
            "method": "getblockheaderbyheight",
            "params": {"height": height}
        }
        r = self.make_request(data, "json_rpc")
        response = r.json()
        block = MoneroBlock()
        block.header_from_rpc(response)
        return block

    def get_transactions(self, tx_hashes):
        """

        :rtype: list[MoneroTransaction]
        """
        data = {
            "txs_hashes": tx_hashes,
            "decode_as_json": True
        }
        r = self.make_request(data, "gettransactions")
        response = r.json()
        txs = []
        for tx, tx_hash in zip(response["txs_as_json"], tx_hashes):
            mtx = MoneroTransaction(tx_hash)
            mtx.from_rpc(json.loads(tx))
            txs.append(mtx)
        return txs


class MoneroBlock(object):
    def __init__(self):
        self.height = None
        self.block_hash = None
        self.timestamp = None
        self.coinbase = None
        self.tx_hashes = []

    def from_rpc(self, response_json):
        self.header_from_rpc(response_json)

        content = json.loads(response_json["result"]["json"])
        tx = MoneroTransaction()
        tx.coinbase_from_rpc(content["miner_tx"])
        self.coinbase = tx
        self.tx_hashes = content["tx_hashes"]

    def header_from_rpc(self, response_json):
        header = response_json["result"]["block_header"]
        self.block_hash = header["hash"]
        self.height = header["height"]
        self.timestamp = header["timestamp"]


class MoneroTransaction(object):
    def __init__(self, tx_hash = ""):
        self.tx_hash = tx_hash
        self.inputs = []
        self.outputs = []
        self.fee = None

    def coinbase_from_rpc(self, obj):
        # RCT coinbase transactions have a single output that counts as a 0-value output
        # https://github.com/monero-project/monero/blob/c534fe8d19aa20a30849ca123f0bd90314659970/src/blockchain_db/blockchain_db.cpp#L179
        if obj["version"] == 2:
            self.outputs.append(MoneroOutput(0))
        else:
            for output in obj["vout"]:
                self.outputs.append(
                    MoneroOutput(output["amount"])
                )
        self.fee = 0

    def from_rpc(self, obj):
        for vin in obj["vin"]:
            self.inputs.append(
                MoneroInput(vin["key"]["amount"], vin["key"]["key_offsets"])
            )

        for vout in obj["vout"]:
            self.outputs.append(
                MoneroOutput(vout["amount"])
            )

        if "rct_signatures" in obj:
            self.fee = obj["rct_signatures"]["txnFee"]
        else:
            self.fee = sum([x.amount for x in self.inputs]) - sum([x.amount for x in self.outputs])


class MoneroOutput(object):
    def __init__(self, amount):
        self.amount = amount


class MoneroInput(object):
    def __init__(self, amount, offsets):
        self.amount = amount
        self.offsets = offsets

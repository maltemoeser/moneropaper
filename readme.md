## Overview

This repository contains source code underlying our [empirical analysis of traceability in the Monero blockchain](https://arxiv.org/pdf/1704.04299.pdf).

Reproducing the analysis involves the following steps

1. Export blockchain data from a fully synchronized Monero node
2. Import the data into a Neo4j database using the Neo4j batch importer
3. Create database indices and run the Sudoku algorithm
4. Querying the database


## 1. Monero Blockchain Data Export

- Install the [Monero software](https://github.com/monero-project/monero/releases), start the daemon and wait for the node to synchronize
- Install the [requests](https://pypi.python.org/pypi/requests) python library: `pip install requests`
- Run the `monero-to-csv.py` script (i.e., `python monero-to-csv.py`)


## 2. Neo4j Import

- Download and/or install the [Neo4j graph database v3.4.5](https://neo4j.com/download-thanks/?edition=community&release=3.4.5&flavour=unix)
- You should now have a folder structure similar to this:
    - `./csv/`: the Monero blockchain export (CSV files)
    - `./csv-headers/`: the CSV header files
- Use the following import script to use the `neo4j-import` tool for a quick import (you'll need to modify the `--into <DIRECTORY>` directory to match the location of your Neo4j installation)

```
neo4j-import --into <DIRECTORY> --nodes:Block "csv-headers/blocks.csv,csv/blocks.csv" --relationships:PREV_BLOCK "csv-headers/blocks-rels.csv,csv/blocks-rels.csv" --nodes:Transaction "csv-headers/transactions.csv,csv/transactions.csv" --relationships:IN_BLOCK "csv-headers/tx-blocks.csv,csv/tx-blocks.csv" --nodes:Output "csv-headers/outputs.csv,csv/outputs.csv" --relationships:TX_OUTPUT "csv-headers/output-rels.csv,csv/output-rels.csv" --nodes:Input "csv-headers/inputs.csv,csv/inputs.csv" --relationships:TX_INPUT "csv-headers/input-rels.csv,csv/input-rels.csv" --relationships:REFERENCES "csv-headers/input-output-refs.csv,csv/input-output-refs.csv"
```

- Afterwards, you should see a message similar to the following:
```
IMPORT DONE in 5m 41s 347ms.
Imported:
  45169429 nodes
  84119631 relationships
  109326559 properties
Peak memory usage: 1.51 GB
```


## 3. Database Indexes and Mixin Sudoku

- Compile the Neo4j plugin in the `neo4j-plugin` folder: `mvn package`
- Put the resulting `mixinsudoku.jar` into the `plugins` folder of your Neo4j installation
- Download the [APOC plugin](https://github.com/neo4j-contrib/neo4j-apoc-procedures/releases/3.2.0.4) and also put it into the `plugins` folder
- Allow unrestricted access by adding `dbms.security.procedures.unrestricted=mixinsudoku.*,apoc.*` to your Neo4j config
- Start the database
- Run the following cypher commands (through the web interface or the command line interface)
    - `CALL mixinsudoku.schema()`: creates all necessary indexes (you can check the status with `:SCHEMA`)
    - `CALL mixinsudoku.coinbase()`: adds labels for coinbase transactions
- Run the mixin sudoku in two steps (run the second query only *after* the first one has finished):
    1. `CALL mixinsudoku.zeromixin()`: labels all outputs spent by 0-mixin transactions
    2. `CALL mixinsudoku.sudoku()`: iteratively labels further deducable outputs
- Run `CALL mixinsudoku.checkdb()` as a sanity check at the end


## 4. Jupyter notebook

- Install the following Python modules:
    - `pip install jupyter`
    - `pip install pandas`
    - `pip install seaborn`
    - `pip install py2neo`
- Launch the notebook server: `jupyter notebook &`
- Open *Monero Analysis.ipynb* in the notebook interface

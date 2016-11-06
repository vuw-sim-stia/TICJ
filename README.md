# TICJ
Java application to generate Transcendental Information Cascades

## Usage

java -jar CascadeDataStream.jar -i -p -c -s [source 1] -db [host] [database] [username] [password]

## Input data structure

CSV file of the form

[item id];[item timestamp];[matched identifier 1], [matched identifier 2], [matched identifier 3], ..., [matched identifier n]

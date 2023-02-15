I definitions.inc
> init
<<<
+ wug.txt wug.txt
> add wug.txt
<<<
> commit "version 1 of wug.txt"
<<<
+ wug.txt notwug.txt
> add wug.txt
<<<
> commit "version 2 of wug.txt"
<<<
= wug.txt notwug.txt
# Each ${COMMIT_HEAD_T} captures its commit UID.
> log
===
${COMMIT_HEAD_T}
version 2 of wug.txt

===
${COMMIT_HEAD_T}
version 1 of wug.txt

===
${COMMIT_HEAD_T}
initial commit

<<<*
# UID of second version
D UID2 "${1}"
# UID of current version
D UID1 "${2}"
# UID of initial version
D UID0 "${3}"
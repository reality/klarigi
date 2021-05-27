# Klarigi

This application will give you explanations for clusters or groupings 
of entities described by ontology classes. For example, groups of
patient profiles described by HPO classes. 

## Guide

### Data Setup and Formatting

You will need a file that describes your entities, their ontology associations,
and groupings. This is provided using the *--data* argument. The file should
be a simple tab-separated-values file. The first column should be the ID of 
an entity (if you have no ID, you can just use an incremental number), the 
second should be a semi-colon delimited list of ontology terms
associated with that entity. The third should be an identifier for 
the group that this entity is in. This can be any string.

So for example, here is a small example of patients described with HPO:

```
0 HP:0001510;HP:0040315 OMIM:604271
1 HP:0004322;HP:0012496 OMIM:604271
2 HP:0004322;HP:0009279 OMIM:604271
3 HP:0001510;HP:0031960 OMIM:604271
4 HP:0004322;HP:0012515 OMIM:604271
5 HP:0012373;HP:0000565;HP:0012372;HP:0007773;HP:0007903;HP:0003779 OMIM:172870
6 HP:0000539;HP:0000565;HP:0004329;HP:0004329;HP:0007903;HP:0004467 OMIM:172870
7 HP:0000540;HP:0032012;HP:0007737;HP:0007773;HP:0007903;HP:0012234 OMIM:172870
8 HP:0000540;HP:0000565;HP:0012372;HP:0004329;HP:0007903;HP:0012597 OMIM:172870
9 HP:0000540;HP:0000565;HP:0007737;HP:0004327;HP:0007903;HP:0001557 OMIM:172870
```

So we have ten patients here, with an incrementing identifier in the first
column. The second column is the list of ontology term IDs associated with each
patient. The third is the the 'group' that this patient is in - in this case,
it is an OMIM rare disease identifier.

You will need the ontology that describes your patients, such as hp.owl
downloaded locally.


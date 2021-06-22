# Klarigi

This application will give you explanations for clusters or groupings 
of entities described by ontology classes. For example, groups of
patient profiles described by HPO classes. 

Useful Links and Information:
* Check out this [tutorial](https://colab.research.google.com/drive/18BxV-mOItKpOu_rtrb1_WSnXeB4YsRYi?usp=sharing) to be walked through some examples!
* Papers:
  * [Klarigi: Explanations for Semantic Clusters](https://www.biorxiv.org/content/10.1101/2021.06.14.448423v1)
  * [Multi-faceted Semantic Clustering With Text-derived Phenotypes](https://www.medrxiv.org/content/10.1101/2021.05.26.21257830v1)
* Important: [This release](https://github.com/reality/klarigi/releases/tag/0.0.8) is the last version with the algorithm exactly as described by the papers. Subsequent versions are experimental, involving some different heuristics and calculation methods!

## Guide

### Data Setup and Formatting

You will need a file that describes your entities, their ontology associations,
and groupings. This is provided using the *--data* argument. The file should
be a simple tab-separated-values file. The first column should be the ID of 
an entity (if you have no ID, you can just use an incremental number), the 
second should be a semi-colon delimited list of ontology terms
associated with that entity. The third should be an identifier for 
the group that this entity is in. This can be any string.

So for example, here is a small example of patients described with HPO (if you're following this as a tutorial example, save this to *data.txt*):

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
downloaded locally. To acquire it for this tutorial example, you can run the
following command

```bash
wget http://purl.obolibrary.org/obo/hp.owl
```

### Run

So, to run the program minimally, you can type:

```bash
./klarigi --data data.txt --ontology hp.owl --group OMIM:604271
```

The *--data* argument is the path to the file containing entities/groupings (as above), *--ontology* is the path to the ontology

### What's it doing?

So, when you run the application, let's say with the command above, here is roughly what happens

1. Load the data file.
2. Load and classify the ontology using the ELK reasoner.
3. Obtain information content values for each ontology class associated with the entities in your data file using SML.
4. Score all relevant classes with inclusivity/exclusivity/information content scores. Relevant classes are directly annotated entities, and all of their superclasses.
5. Run <i>The Explanation Algorithm</i> using the scores. Generate the set of explanations for the group given by *--group*.

### Options

The above is the general workflow, however there are a number of command line options you can use to modify this behaviour.

#### Information Content

By default, information content values will be calculated every time. However, you can also provide your own information content values! All you have to do is use the *--ic* argument to pass the path to a file that contains the information content values for the classes. It simply has to be a tab-separated-values file in which the first column is the full IRI of the class, and the second is the information content value. 

You can also save such a file as an output of the program using the *--save-ic myfile.txt* argument. This will save the values it calculates into a file that can be passed with *--ic* next time around. This may speed things up, especially if your ontology or data is very large.

Danger Warning: if there are unmapped values in your information content file, they won't appear in explanations!

Todo: Currently the IC generation method uses the Zhou method, and it is not configurable. SML supports many other methods, however, so I plan to add additional configurability.

#### Group

We can use the *--group* argument to tell it which grouping to provide an explanation for. If you don't pass this argument, or pass *--group \**, then it will give you explanations for all of the groups described in your data file.


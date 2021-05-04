# FullPath
Search all the path beteween to elements.
## Syntaxis: fullPath(<from: OIdentifiable>, <to: OIdentifiable>[, {parametes}])

### Avaliable parametes:
- maxDepth: <int> set maximum level of depth. Default: 100;
- direction: "IN", "OUT", "BOTH"
- include: [] array of string indicating the class name to include.
- exclude: [] array of string indicating the class name to exclude.

## Example
```Java
OResultSet r = db.query("select fullPath(#58:0,#65:0,{'maxDepth': 10, 'include': ['FullPathTest','path_1'],'exclude': ['path_2']}) as fp;");
``` 


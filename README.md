# FullPath
Search all the path beteween to elements.
## Syntaxis: fullPath(<from: OIdentifiable>, <to: OIdentifiable>[, {parametes}])

### Avaliable parametes:
- maxDepth: <int> set maximum level of depth. Default: 100;
- direction: "IN", "OUT", "BOTH"
- include: [] array of string indicating the class name to include. It match Edges and Vertex class.
- exclude: [] array of string indicating the class name to exclude. It match Edges and Vertex class.
- includeEdge: [] array of string indicating the class name of Edges to include.
- excludeEdge: [] array of string indicating the class name of Edges to exclude.
- includeClasses: [] array of string indicating the class name of to include. All subclasses will be added automatically. It match Edges and Vertex class.
- excludeClasses: [] array of string indicating the class name of to exclude. All subclasses will be added automatically. It match Edges and Vertex class.

## Example
```Java
OResultSet r = db.query("select fullPath(#58:0,#65:0,{'maxDepth': 10, 'include': ['FullPathTest','path_1'],'exclude': ['path_2']}) as fp;");

OResultSet r = db.query("select fullPath((select from FullPathTest where name="n1"),(select from FullPathTest where name="n4"),{'maxDepth': 10, 'include': ['FullPathTest','path_1'],'exclude': ['path_2']}) as fp;");
``` 


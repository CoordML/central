package sircle_lang

object Preload {
  val preloadSource: String =
    """
      |def map = f: Any -> Any => xs: List => for x <- xs do f x
      |
      |def valuesOf = map: { } => for k <- keysOf map do map.k
      |
      |def foldl = op: Any -> Any => z: Any => xs: List => {
      |  def ret = z;
      |  for x <- xs do {
      |    ret = op ret x
      |  };
      |  ret
      |}
      |
      |def foldl1 = op: Any -> Any => xs: List => foldl op (head xs) (tail xs)
      |
      |def prod = xs: List => foldl1 (x: List => y: List => x * y) xs
      |
      |def min = x => y => if x > y then y else x
      |
      |def max = x => y => if x > y then x else y
      |
      |def zipWith = op: Any -> Any -> Any => xs: List => ys: List => {
      |  def len = min (length xs) (length ys);
      |  for i <- range len do
      |    op (xs.i) (ys.i)
      |}
      |
      |def zip = zipWith $ x => y => (x, y)
      |
      |def namedProd = map: {} => {
      |  def keys = keysOf map;
      |  def pairs = map toList $ prod $ valuesOf map;
      |  def namedPairs = map (zip keys) pairs;
      |  map buildMapping namedPairs
      |}
      |
      |def replicate = n: Int => x: Any => for i <- range n do x
      |
      |def cat = foldl1 (x: List => y: List => x + y)
      |
      |def compose = f: Any -> Any => g: Any -> Any => x: Any => f (g x)
      |
      |def flatMap = f: Any -> Any => compose cat $ map f
      |
      |def filter = f: Any -> Any => xs: List => for x <- xs, f x do x
      |
      |type Config =
      |  { "seeds": List
      |  , "datasets": List
      |  , "base_model": List
      |  }
      |
      |def config: Config =
      |  { "seeds" -> [1, 2, 3]
      |  , "datasets" -> ["avazu", "criteo"]
      |  , "base_model" -> ["DAutoInt", "DAutoInt+"]
      |  }
      |
      |def task: Task = mkTask "main.py" config config
      |""".stripMargin
}
package juloo.keyboard2;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class KeyboardData
{
  public final List<Row> rows;
  /** Total width of the keyboard. */
  public final float keysWidth;
  /** Total height of the keyboard. */
  public final float keysHeight;
  /** Whether to add extra keys. */
  public final boolean extra_keys;
  /** Whether to possibly add NumPad. */
  public final boolean num_pad;

  public KeyboardData mapKeys(MapKey f)
  {
    ArrayList<Row> rows_ = new ArrayList<Row>();
    for (Row r : rows)
      rows_.add(r.mapKeys(f));
    return new KeyboardData(rows_, keysWidth, extra_keys, num_pad);
  }

  /** Add keys from the given iterator into the keyboard. Extra keys are added
   * on the empty key4 corner of the second row, from right to left. If there's
   * not enough room, key3 of the second row is tried then key2 and key1 of the
   * third row. */
  public KeyboardData addExtraKeys(Iterator<KeyValue> k)
  {
    if (!extra_keys)
      return this;
    ArrayList<Row> rows = new ArrayList<Row>(this.rows);
    addExtraKeys_to_row(rows, k, 1, 4);
    addExtraKeys_to_row(rows, k, 1, 3);
    addExtraKeys_to_row(rows, k, 2, 2);
    addExtraKeys_to_row(rows, k, 2, 1);
    if (k.hasNext())
    {
      for (int r = 0; r < rows.size(); r++)
        for (int c = 1; c <= 4; c++)
          addExtraKeys_to_row(rows, k, r, c);
    }
    return new KeyboardData(rows, keysWidth, extra_keys, num_pad);
  }

  public KeyboardData addNumPad()
  {
    if (!num_pad || _numPadKeyboardData == null)
      return this;
    ArrayList<Row> extendedRows = new ArrayList<Row>();
    Iterator<Row> iterNumPadRows = _numPadKeyboardData.rows.iterator();
    for (Row row : rows)
    {
      ArrayList<KeyboardData.Key> keys = new ArrayList<Key>(row.keys);
      float height = row.height;
      float shift = row.shift;
      if (iterNumPadRows.hasNext())
      {
        Row numPadRow = iterNumPadRows.next();
        Key k = numPadRow.keys.get(0);
        if (k != null) {
          float firstNumPadShift = 0.5f + keysWidth - row.keysWidth;
          Key shiftedKey = new Key(k.key0, k.key1, k.key2, k.key3, k.key4, k.width, firstNumPadShift, k.edgekeys);
          numPadRow.keys.set(0, shiftedKey);
        }
        keys.addAll(numPadRow.keys);
        height = Math.max(height, numPadRow.height);
        shift = Math.max(shift, numPadRow.shift);
      }
      extendedRows.add(new Row(keys, height, shift));
    }
    return new KeyboardData(extendedRows, compute_max_width(extendedRows), extra_keys, num_pad);
  }

  public Key findKeyWithValue(KeyValue kv)
  {
    for (Row r : rows)
    {
      Key k = r.findKeyWithValue(kv);
      if (k != null)
        return k;
    }
    return null;
  }

  private static void addExtraKeys_to_row(ArrayList<Row> rows, final Iterator<KeyValue> extra_keys, int row_i, final int d)
  {
    if (!extra_keys.hasNext())
      return;
    rows.set(row_i, rows.get(row_i).mapKeys(new MapKey(){
      public Key apply(Key k) {
        if (k.getKeyValue(d) == null && extra_keys.hasNext())
          return k.withKeyValue(d, extra_keys.next());
        else
          return k;
      }
    }));
  }

  private static Row _bottomRow = null;
  private static KeyboardData _numPadKeyboardData = null;
  private static Map<Integer, KeyboardData> _layoutCache = new HashMap<Integer, KeyboardData>();

  public static KeyboardData load(Resources res, int id)
  {
    KeyboardData l = _layoutCache.get(id);
    if (l == null)
    {
      try
      {
        if (_bottomRow == null)
          _bottomRow = parse_bottom_row(res.getXml(R.xml.bottom_row));
        if (_numPadKeyboardData == null)
        {
          _numPadKeyboardData = parse_keyboard(res.getXml(R.xml.numpad));
        }
        l = parse_keyboard(res.getXml(id));
        _layoutCache.put(id, l);
      }
      catch (Exception e)
      {
        e.printStackTrace();
      }
    }
    return l;
  }

  private static KeyboardData parse_keyboard(XmlResourceParser parser) throws Exception
  {
    if (!expect_tag(parser, "keyboard"))
      throw new Exception("Empty layout file");
    boolean bottom_row = parser.getAttributeBooleanValue(null, "bottom_row", true);
    boolean extra_keys = parser.getAttributeBooleanValue(null, "extra_keys", true);
    boolean num_pad = parser.getAttributeBooleanValue(null, "num_pad", true);
    ArrayList<Row> rows = new ArrayList<Row>();
    while (expect_tag(parser, "row"))
        rows.add(Row.parse(parser));
    float kw = compute_max_width(rows);
    if (bottom_row)
      rows.add(_bottomRow.updateWidth(kw));
    return new KeyboardData(rows, kw, extra_keys, num_pad);
  }

  private static float compute_max_width(List<Row> rows)
  {
    float w = 0.f;
    for (Row r : rows)
      w = Math.max(w, r.keysWidth);
    return w;
  }

  private static Row parse_bottom_row(XmlResourceParser parser) throws Exception
  {
    if (!expect_tag(parser, "row"))
      throw new Exception("Failed to parse bottom row");
    return Row.parse(parser);
  }

  protected KeyboardData(List<Row> rows_, float kw, boolean xk, boolean np)
  {
    float kh = 0.f;
    for (Row r : rows_)
      kh += r.height + r.shift;
    rows = rows_;
    keysWidth = kw;
    keysHeight = kh;
    extra_keys = xk;
    num_pad = np;
  }

  public static class Row
  {
    public final List<Key> keys;
    /** Height of the row, without 'shift'. */
    public final float height;
    /** Extra empty space on the top. */
    public final float shift;
    /** Total width of the row. */
    public final float keysWidth;

    protected Row(List<Key> keys_, float h, float s)
    {
      float kw = 0.f;
      for (Key k : keys_) kw += k.width + k.shift;
      keys = keys_;
      height = h;
      shift = s;
      keysWidth = kw;
    }

    public static Row parse(XmlResourceParser parser) throws Exception
    {
      ArrayList<Key> keys = new ArrayList<Key>();
      int status;
      float h = parser.getAttributeFloatValue(null, "height", 1f);
      float shift = parser.getAttributeFloatValue(null, "shift", 0f);
      while (expect_tag(parser, "key"))
        keys.add(Key.parse(parser));
      return new Row(keys, h, shift);
    }

    public Row mapKeys(MapKey f)
    {
      ArrayList<Key> keys_ = new ArrayList<Key>();
      for (Key k : keys)
        keys_.add(f.apply(k));
      return new Row(keys_, height, shift);
    }

    /** Change the width of every keys so that the row is 's' units wide. */
    public Row updateWidth(float newWidth)
    {
      final float s = newWidth / keysWidth;
      return mapKeys(new MapKey(){
        public Key apply(Key k) { return k.scaleWidth(s); }
      });
    }

    public Key findKeyWithValue(KeyValue kv)
    {
      for (Key k : keys)
        if (k.hasValue(kv))
          return k;
      return null;
    }
  }

  public static class Key
  {
    /*
     ** 1   2
     **   0
     ** 3   4
     */
    public final Corner key0;
    public final Corner key1;
    public final Corner key2;
    public final Corner key3;
    public final Corner key4;

    /** Key width in relative unit. */
    public final float width;
    /** Extra empty space on the left of the key. */
    public final float shift;
    /** Put keys 1 to 4 on the edges instead of the corners. */
    public final boolean edgekeys;

    protected Key(Corner k0, Corner k1, Corner k2, Corner k3, Corner k4, float w, float s, boolean e)
    {
      key0 = k0;
      key1 = k1;
      key2 = k2;
      key3 = k3;
      key4 = k4;
      width = w;
      shift = s;
      edgekeys = e;
    }

    public static Key parse(XmlResourceParser parser) throws Exception
    {
      Corner k0 = Corner.parse_of_attr(parser, "key0");
      Corner k1 = Corner.parse_of_attr(parser, "key1");
      Corner k2 = Corner.parse_of_attr(parser, "key2");
      Corner k3 = Corner.parse_of_attr(parser, "key3");
      Corner k4 = Corner.parse_of_attr(parser, "key4");
      float width = parser.getAttributeFloatValue(null, "width", 1f);
      float shift = parser.getAttributeFloatValue(null, "shift", 0.f);
      boolean edgekeys = parser.getAttributeBooleanValue(null, "edgekeys", false);
      while (parser.next() != XmlResourceParser.END_TAG)
        continue ;
      return new Key(k0, k1, k2, k3, k4, width, shift, edgekeys);
    }

    /** New key with the width multiplied by 's'. */
    public Key scaleWidth(float s)
    {
      return new Key(key0, key1, key2, key3, key4, width * s, shift, edgekeys);
    }

    public KeyValue getKeyValue(int i)
    {
      Corner c;
      switch (i)
      {
        case 0: c = key0; break;
        case 1: c = key1; break;
        case 2: c = key2; break;
        case 3: c = key3; break;
        case 4: c = key4; break;
        default: c = null; break;
      }
      return (c == null) ? null : c.kv;
    }

    public Key withKeyValue(int i, KeyValue kv)
    {
      Corner k0 = key0, k1 = key1, k2 = key2, k3 = key3, k4 = key4;
      Corner k = Corner.of_kv(kv);
      switch (i)
      {
        case 0: k0 = k; break;
        case 1: k1 = k; break;
        case 2: k2 = k; break;
        case 3: k3 = k; break;
        case 4: k4 = k; break;
      }
      return new Key(k0, k1, k2, k3, k4, width, shift, edgekeys);
    }

    /**
     * See Pointers.onTouchMove() for the represented direction.
     */
    public KeyValue getAtDirection(int direction)
    {
      Corner c = null;
      if (edgekeys)
      {
        // \ 1 /
        //  \ /
        // 3 0 2
        //  / \
        // / 4 \
        switch (direction)
        {
          case 2: case 3: c = key1; break;
          case 4: case 5: c = key2; break;
          case 6: case 7: c = key4; break;
          case 8: case 1: c = key3; break;
        }
      }
      else
      {
        // 1 | 2
        //   |
        // --0--
        //   |
        // 3 | 4
        switch (direction)
        {
          case 1: case 2: c = key1; break;
          case 3: case 4: c = key2; break;
          case 5: case 6: c = key4; break;
          case 7: case 8: c = key3; break;
        }
      }
      return (c == null) ? null : c.kv;
    }

    public boolean hasValue(KeyValue kv)
    {
      return (hasValue(key0, kv) || hasValue(key1, kv) || hasValue(key2, kv) ||
          hasValue(key3, kv) || hasValue(key4, kv));
    }

    private static boolean hasValue(Corner c, KeyValue kv)
    {
      return (c != null && c.kv.equals(kv));
    }
  }

  public static final class Corner
  {
    public final KeyValue kv;
    /** Whether the kv is marked with the "loc " prefix. To be removed if not
        specified in the [extra_keys]. */
    public final boolean localized;

    protected Corner(KeyValue k, boolean l)
    {
      kv = k;
      localized = l;
    }

    public static Corner parse_of_attr(XmlResourceParser parser, String attr) throws Exception
    {
      String name = parser.getAttributeValue(null, attr);
      boolean localized = false;

      if (name == null)
        return null;
      String name_loc = stripPrefix(name, "loc ");
      if (name_loc != null)
      {
        localized = true;
        name = name_loc;
      }
      return new Corner(KeyValue.getKeyByName(name), localized);
    }

    public static Corner of_kv(KeyValue kv)
    {
      return new Corner(kv, false);
    }

    private static String stripPrefix(String s, String prefix)
    {
      if (s.startsWith(prefix))
        return s.substring(prefix.length());
      else
        return null;
    }
  }

  // Not using Function<KeyValue, KeyValue> to keep compatibility with Android 6.
  public static abstract interface MapKey {
    public Key apply(Key k);
  }

  public static abstract class MapKeyValues implements MapKey {
    abstract public KeyValue apply(KeyValue c, boolean localized);

    public Key apply(Key k)
    {
      return new Key(apply(k.key0), apply(k.key1), apply(k.key2),
          apply(k.key3), apply(k.key4), k.width, k.shift, k.edgekeys);
    }

    private Corner apply(Corner c)
    {
      if (c == null)
        return null;
      KeyValue kv = apply(c.kv, c.localized);
      if (kv == null)
        return null;
      return Corner.of_kv(kv);
    }
  }

  /** Parsing utils */

  /** Returns [false] on [END_DOCUMENT] or [END_TAG], [true] otherwise. */
  private static boolean expect_tag(XmlResourceParser parser, String name) throws Exception
  {
    int status;
    do
    {
      status = parser.next();
      if (status == XmlResourceParser.END_DOCUMENT || status == XmlResourceParser.END_TAG)
        return false;
    }
    while (status != XmlResourceParser.START_TAG);
    if (!parser.getName().equals(name))
      throw new Exception("Unknow tag: " + parser.getName());
    return true;
  }
}

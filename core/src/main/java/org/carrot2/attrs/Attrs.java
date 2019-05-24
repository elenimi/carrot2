/*
 * Carrot2 project.
 *
 * Copyright (C) 2002-2019, Dawid Weiss, Stanisław Osiński.
 * All rights reserved.
 *
 * Refer to the full license file "carrot2.LICENSE"
 * in the root folder of the repository checkout or at:
 * https://www.carrot2.org/carrot2.LICENSE
 */
package org.carrot2.attrs;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Attrs {
  static final String KEY_TYPE = "@type";
  static final String KEY_WRAPPED = "@value";

  private static class Wrapper implements AcceptingVisitor {
    AttrObject<AcceptingVisitor> value =
        AttrObject.builder(AcceptingVisitor.class).defaultValue(() -> null);

    @Override
    public void accept(AttrVisitor visitor) {
      visitor.visit(KEY_WRAPPED, value);
    }
  }

  public static Map<String, Object> toMap(AcceptingVisitor composite) {
    return toMap(composite, AliasMapper.SPI_DEFAULTS::toName);
  }

  public static Map<String, Object> toMap(
      AcceptingVisitor composite, Function<Object, String> classToName) {
    LinkedHashMap<String, Object> map = new LinkedHashMap<>();

    Wrapper wrapped = new Wrapper();
    wrapped.value.set(composite);
    wrapped.accept(new ToMapVisitor(map, classToName));

    @SuppressWarnings("unchecked")
    Map<String, Object> sub = (Map<String, Object>) map.get(KEY_WRAPPED);
    return sub;
  }

  public static <E extends AcceptingVisitor> E fromMap(
      Class<? extends E> clazz, Map<String, Object> map) {
    return fromMap(clazz, map, AliasMapper.SPI_DEFAULTS::fromName);
  }

  public static <E extends AcceptingVisitor> E fromMap(
      Class<? extends E> clazz, Map<String, Object> map, Function<String, Object> nameToClass) {
    Wrapper wrapper =
        populate(new Wrapper(), Collections.singletonMap(KEY_WRAPPED, map), nameToClass);
    return safeCast(wrapper.value.get(), KEY_WRAPPED, clazz);
  }

  public static <E extends AcceptingVisitor> E populate(
      E instance, Map<String, Object> map, Function<String, Object> nameToClass) {
    FromMapVisitor visitor = new FromMapVisitor(map, nameToClass);
    instance.accept(visitor);
    visitor.ensureKeysConsumed();

    return instance;
  }

  private static class FromMapVisitor implements AttrVisitor {
    private final Map<String, Object> map;
    private final Function<String, Object> classToInstance;

    private FromMapVisitor(Map<String, Object> map, Function<String, Object> classToInstance) {
      this.map = new LinkedHashMap<>(Objects.requireNonNull(map));
      this.classToInstance = classToInstance;
    }

    @Override
    public void visit(String key, AttrInteger attr) {
      if (map.containsKey(key)) {
        Number value = safeCast(map.remove(key), key, Number.class);
        if (value != null) {
          if (Double.compare(value.doubleValue(), value.longValue()) != 0) {
            throw new IllegalArgumentException(
                String.format(
                    Locale.ROOT,
                    "Value at key '%s' should be an integer but encountered floating point value: '%s'",
                    key,
                    toDebugString(value)));
          }

          long v = value.longValue();
          if ((int) v != v) {
            throw new IllegalArgumentException(
                String.format(
                    Locale.ROOT,
                    "Value at key '%s' should be an integer but is out of integer range: '%s'",
                    key,
                    toDebugString(value)));
          }
          attr.set((int) v);
        } else {
          attr.set(null);
        }
      }
    }

    @Override
    public void visit(String key, AttrDouble attr) {
      if (map.containsKey(key)) {
        Number value = safeCast(map.remove(key), key, Number.class);
        attr.set(value == null ? null : value.doubleValue());
      }
    }

    @Override
    public void visit(String key, AttrBoolean attr) {
      if (map.containsKey(key)) {
        attr.set(safeCast(map.remove(key), key, Boolean.class));
      }
    }

    @Override
    public <T extends AcceptingVisitor> void visit(String key, AttrObject<T> attr) {
      if (map.containsKey(key)) {
        @SuppressWarnings("unchecked")
        Map<String, Object> submap = safeCast(this.map.remove(key), key, Map.class);
        if (submap == null) {
          attr.set(null);
        } else {
          submap = new LinkedHashMap<>(submap);
          T value;
          if (submap.containsKey(KEY_TYPE)) {
            String valueType = safeCast(submap.remove(KEY_TYPE), KEY_TYPE, String.class);
            value = safeCast(classToInstance.apply(valueType), key, attr.getInterfaceClass());
          } else {
            value = notNull(key, attr.newDefaultValue());
          }

          FromMapVisitor visitor = new FromMapVisitor(submap, classToInstance);
          value.accept(visitor);
          visitor.ensureKeysConsumed();

          attr.set(value);
        }
      }
    }

    @Override
    public <T extends AcceptingVisitor> void visit(String key, AttrObjectArray<T> attr) {
      if (map.containsKey(key)) {
        Object value = map.remove(key);
        if (value == null) {
          attr.set(null);
        } else {
          List<?> array;
          if (value instanceof List) {
            array = (List<?>) value;
          } else if (value instanceof Object[]) {
            array = Arrays.asList((Object[]) value);
          } else {
            throw new IllegalArgumentException(
                String.format(
                    Locale.ROOT,
                    "Value at key '%s' should be a list or an array of objects of type '%s' but is an instance of '%s': '%s'",
                    key,
                    attr.getInterfaceClass().getSimpleName(),
                    value.getClass().getSimpleName(),
                    toDebugString(value)));
          }

          List<T> entries =
              array.stream()
                  .map(
                      entry -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> submap = safeCast(entry, key, Map.class);
                        if (submap == null) {
                          return null;
                        } else {
                          submap = new LinkedHashMap<>(submap);
                          T entryValue;
                          if (submap.containsKey(KEY_TYPE)) {
                            String entryValueType =
                                safeCast(submap.remove(KEY_TYPE), KEY_TYPE, String.class);
                            entryValue =
                                safeCast(
                                    classToInstance.apply(entryValueType),
                                    key,
                                    attr.getInterfaceClass());
                          } else {
                            entryValue = notNull(key, attr.newDefaultEntryValue());
                          }

                          FromMapVisitor visitor = new FromMapVisitor(submap, classToInstance);
                          entryValue.accept(visitor);
                          visitor.ensureKeysConsumed();

                          return entryValue;
                        }
                      })
                  .collect(Collectors.toList());

          attr.set(entries);
        }
      }
    }

    @Override
    public <T extends Enum<T>> void visit(String key, AttrEnum<T> attr) {
      if (map.containsKey(key)) {
        Object value = map.remove(key);
        if (value == null) {
          attr.set((T) null);
        } else if (value instanceof String) {
          try {
            attr.set(Enum.valueOf(attr.enumClass(), (String) value));
          } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                String.format(
                    Locale.ROOT,
                    "Value at key '%s' should be an enum constant of class '%s', but no such constant exists: '%s' (available constants: %s)",
                    key,
                    attr.enumClass().getSimpleName(),
                    toDebugString(value),
                    EnumSet.allOf(attr.enumClass())));
          }
        } else {
          attr.set(safeCast(value, key, attr.enumClass()));
        }
      }
    }

    @Override
    public void visit(String key, AttrStringArray attr) {
      if (map.containsKey(key)) {
        attr.set(safeCast(map.remove(key), key, String[].class));
      }
    }

    @Override
    public void visit(String key, AttrString attr) {
      if (map.containsKey(key)) {
        attr.set(safeCast(map.remove(key), key, String.class));
      }
    }

    private <T> T notNull(String key, T value) {
      if (value == null) {
        throw new RuntimeException(
            String.format(
                Locale.ROOT, "Default instance supplier returned null for key: '%s'", key));
      }
      return value;
    }

    public void ensureKeysConsumed() {
      if (!map.isEmpty()) {
        throw new IllegalArgumentException(
            String.format(
                Locale.ROOT,
                "Unrecognized extra attributes with keys: %s",
                String.join(", ", map.keySet())));
      }
    }
  }

  static <T> T safeCast(Object value, String key, Class<T> clazz) {
    if (value == null) {
      return null;
    } else {
      if (!clazz.isInstance(value)) {
        throw new IllegalArgumentException(
            String.format(
                Locale.ROOT,
                "Value at key '%s' should be an instance of '%s', but encountered class '%s': '%s'",
                key,
                clazz.getSimpleName(),
                value.getClass().getSimpleName(),
                toDebugString(value)));
      }
      return clazz.cast(value);
    }
  }

  static String toDebugString(Object value) {
    if (value == null) {
      return "[null]";
    } else if (value instanceof Object[]) {
      return Arrays.deepToString(((Object[]) value));
    } else {
      return Objects.toString(value);
    }
  }

  private static class ToMapVisitor implements AttrVisitor {
    private final Map<String, Object> map;
    private final Function<Object, String> objectToClass;

    public ToMapVisitor(Map<String, Object> map, Function<Object, String> objectToClass) {
      this.map = map;
      this.objectToClass = objectToClass;
    }

    @Override
    public void visit(String key, AttrInteger attr) {
      ensureNoExistingKey(map, key);
      map.put(key, attr.get());
    }

    @Override
    public void visit(String key, AttrDouble attr) {
      ensureNoExistingKey(map, key);
      map.put(key, attr.get());
    }

    @Override
    public void visit(String key, AttrBoolean attr) {
      ensureNoExistingKey(map, key);
      map.put(key, attr.get());
    }

    @Override
    public <T extends AcceptingVisitor> void visit(String key, AttrObject<T> attr) {
      ensureNoExistingKey(map, key);
      AcceptingVisitor currentValue = attr.get();
      if (currentValue != null) {
        Map<String, Object> submap = new LinkedHashMap<>();
        if (!attr.isDefaultClass(currentValue)) {
          submap.put(KEY_TYPE, objectToClass.apply(currentValue));
        }
        currentValue.accept(new ToMapVisitor(submap, objectToClass));
        map.put(key, submap);
      } else {
        map.put(key, null);
      }
    }

    @Override
    public <T extends AcceptingVisitor> void visit(String key, AttrObjectArray<T> attr) {
      ensureNoExistingKey(map, key);

      List<T> values = attr.get();
      if (values != null) {
        Object[] array =
            values.stream()
                .map(
                    currentValue -> {
                      Map<String, Object> submap = new LinkedHashMap<>();
                      if (!attr.isDefaultClass(currentValue)) {
                        submap.put(KEY_TYPE, objectToClass.apply(currentValue));
                      }
                      currentValue.accept(new ToMapVisitor(submap, objectToClass));
                      return submap;
                    })
                .toArray();
        map.put(key, array);
      } else {
        map.put(key, null);
      }
    }

    @Override
    public <T extends Enum<T>> void visit(String key, AttrEnum<T> attr) {
      ensureNoExistingKey(map, key);
      map.put(key, attr.get() != null ? attr.get().name() : null);
    }

    @Override
    public void visit(String key, AttrStringArray attr) {
      ensureNoExistingKey(map, key);
      map.put(key, attr.get());
    }

    @Override
    public void visit(String key, AttrString attr) {
      ensureNoExistingKey(map, key);
      map.put(key, attr.get());
    }

    private void ensureNoExistingKey(Map<?, ?> map, String key) {
      if (map.containsKey(key)) {
        throw new RuntimeException(
            String.format(
                Locale.ROOT,
                "Could not serialize key '%s' because it already exists in the map with value: %s",
                key,
                map.get(key)));
      }
    }
  }

  public static String toPrettyString(AcceptingVisitor ob, ClassNameMapper mapper) {
    StringBuilder builder = new StringBuilder();
    StringBuilder indent = new StringBuilder();

    ob.accept(
        new AttrVisitor() {
          @Override
          public void visit(String key, AttrBoolean attr) {
            append(key, attr.get());
          }

          @Override
          public void visit(String key, AttrInteger attr) {
            append(key, attr.get());
          }

          @Override
          public void visit(String key, AttrDouble attr) {
            append(key, attr.get());
          }

          @Override
          public <T extends Enum<T>> void visit(String key, AttrEnum<T> attr) {
            T value = attr.get();
            append(key, value == null ? value : "\"" + value + '"');
          }

          @Override
          public void visit(String key, AttrString attr) {
            append(key, attr.get());
          }

          @Override
          public void visit(String key, AttrStringArray attr) {
            String[] value = attr.get();
            if (value == null) {
              append(key, value);
            } else {
              builder.append(indent).append(key).append(": [\n");
              for (String v : value) {
                builder.append(indent).append("  \"").append(v).append("\"\n");
              }
              builder.append(indent).append("]\n");
            }
          }

          @Override
          public <T extends AcceptingVisitor> void visit(String key, AttrObject<T> attr) {
            AcceptingVisitor value = attr.get();
            if (value == null) {
              append(key, value);
            } else {
              builder.append(indent).append(key).append(": {\n");
              int len = indent.length();
              indent.append("  ");
              value.accept(this);
              indent.setLength(len);
              builder.append(indent).append("}\n");
            }
          }

          @Override
          public <T extends AcceptingVisitor> void visit(String key, AttrObjectArray<T> attr) {
            List<T> value = attr.get();
            if (value == null) {
              append(key, value);
            } else {
              int len1 = indent.length();
              builder.append(indent).append(key).append(": [\n");
              indent.append("  ");
              for (AcceptingVisitor v : value) {
                builder.append(indent).append("{\n");
                int len2 = indent.length();
                indent.append("  ");
                v.accept(this);
                indent.setLength(len2);
                builder.append(indent).append("}\n");
              }
              indent.setLength(len1);
              builder.append(indent).append("]\n");
            }
          }

          private void append(String key, Object value) {
            builder.append(indent).append(key).append(": ").append(value).append('\n');
          }
        });

    return builder.toString();
  }
}
# Mapper XML

MyBatis の SQL と `resultMap` を置く。`application.yaml` の
`mybatis.mapper-locations: classpath:mapper/*.xml` が読み込む。

**Mapper インターフェース側には SQL を書かない。**`@Select` などの注釈は使わず、
すべてここに集約する（理由は `repository/package-info.java`）。

## 命名と対応

```
repository/UserMapper.java   ←→   mapper/UserMapper.xml
```

`namespace` は Mapper インターフェースの完全修飾名と厳密に一致させる。ずれると
起動時ではなく**そのメソッドを呼んだ時点**で `BindingException` になる。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
    "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.spacereserve.repository.UserMapper">

  <resultMap id="userResultMap" type="com.example.spacereserve.domain.User">
    <id     property="id"   column="id"/>
    <result property="role" column="role"/>
  </resultMap>

  <select id="findByEmail" resultMap="userResultMap">
    SELECT id, email, password_hash, display_name, role, enabled, created_at, updated_at
      FROM users
     WHERE email = #{email}
  </select>

</mapper>
```

## 守ること

**`#{}` を使う。`${}` を使わない。** `#{}` は `PreparedStatement` のプレースホルダに
なるが、`${}` は文字列をそのまま埋め込むため SQL インジェクションになる。動的に
差し込みたいのが ORDER BY のカラム名など識別子の場合に限り `${}` が必要になるが、
その場合は許可値の列挙と突き合わせてから使うこと。

**`SELECT *` を書かない。** カラムを追加したときに `resultMap` に無い列が増え、
`map-underscore-to-camel-case` の自動マッピングが意図しない挙動をする。

**`map-underscore-to-camel-case: true` が効いている。** `password_hash` →
`passwordHash` のように素直に対応する列は `<result>` を書かなくてよい。`resultMap` に
明示するのは `<id>` と、命名が対応しない列、`<association>` で組み立てる値オブジェクト。

**モデルにフィールドを足したら、この XML の SELECT 句も直す。** MyBatis には起動時の
スキーマ検証が無いため、書き忘れるとそのフィールドは黙って `null` のままになる。
`db/migration` と合わせて3点セットで直すこと。

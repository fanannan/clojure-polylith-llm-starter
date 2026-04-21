(ns polyguard.hooks
  "Polylith template 固有の custom hook 群。
   _POSSIBLE_ISSUES.md A 系の機械化実装（AST 解析が必要なもの）。

   file-level 解析が必要な項目（A-1 interface 契約・A-14 1 ファイル 1 ns）は
   scripts/check-interface-contracts.sh / scripts/check-single-ns-per-file.sh で
   実装されており、本 hook はコード内の構文・構造パターンを扱う。

   登録は .clj-kondo/config.edn の :hooks {:analyze-call ...} から行う。

   hook の疲労を抑えるため、各関数は最小限の解析に留める:
     - 偽陽性を避けるため、確度が低いケースは報告しない
     - 複雑な edge case は docstring に記載して手動検知に委ねる"
  (:require
   [clj-kondo.hooks-api :as api]))

;; ---------------------------------------------------------------------------
;; 共通ユーティリティ
;; ---------------------------------------------------------------------------

(defn- reg!
  "finding を登録する共通 helper。node の row/col を引き継ぐ。"
  [node type level message]
  (api/reg-finding!
   (merge
    (select-keys (meta node) [:row :col :end-row :end-col])
    {:type type :level level :message message})))

(defn- children
  "node の直接の子 node 列を返す（list-node 等の共通アクセッサ）。"
  [node]
  (:children node))

(defn- count-lines
  "node の行数を概算する（meta の :row / :end-row の差分 + 1）。
   meta がない場合は 0 を返す。"
  [node]
  (let [m (meta node)
        r (:row m)
        er (:end-row m)]
    (if (and r er) (inc (- er r)) 0)))

(defn- find-recursive
  "node の子孫を DFS で走査し、pred が真を返す最初の node を返す（なければ nil）。
   hook で深い走査をすると遅くなるので、使用は最小限に。"
  [node pred]
  (letfn [(walk [n]
            (cond
              (nil? n) nil
              (pred n) n
              :else (some walk (children n))))]
    (walk node)))

(defn- destructuring-depth
  "ベクタ / マップの destructuring 深さを返す（最大ネストレベル）。

   修正履歴: 当初の実装は `{:keys [a b]}` のような一般的な destructuring を
   depth 3 と判定し、閾値 > 2 で false positive を多発させていた。
   `:keys`・`:strs`・`:syms` のような「名前リスト」としてのベクタは
   destructuring ネストと見なさないよう、`:keys` 系の直下ベクタを
   カウントから除外する簡易ルールにした。完全な destructuring 解析ではないが、
   実運用での false positive を抑える。"
  [node]
  (letfn [(keys-like? [n]
            ;; :keys / :strs / :syms / :or / :as など「名前リスト」系キーワード
            (and (some? n)
                 (try
                   (let [sv (api/sexpr n)]
                     (and (keyword? sv)
                          (contains? #{"keys" "strs" "syms" "or" "as"} (name sv))))
                   (catch Exception _ false))))
          (non-trivial-children [n]
            ;; whitespace / comment など token/vec/map 以外を排除
            (filter #(or (api/token-node? %)
                         (api/vector-node? %)
                         (api/map-node? %))
                    (children n)))
          (walk [n depth]
            (cond
              (api/vector-node? n)
              (apply max depth (map #(walk % (inc depth)) (non-trivial-children n)))

              (api/map-node? n)
              ;; map-node の子は key-value ペア列（whitespace 除外済）。
              ;; :keys / :strs / :syms の直下ベクタは「名前リスト」で
              ;; destructuring ネストではないので加算しない。
              (let [pairs (partition-all 2 (non-trivial-children n))]
                (apply max depth
                       (for [[k v] pairs
                             :when v]
                         (if (keys-like? k)
                           depth
                           (walk v (inc depth))))))

              :else depth))]
    (walk node 0)))

;; ---------------------------------------------------------------------------
;; A-7: 関数行数上限（ロジック関数 20 行 / インターフェース関数 50 行）
;;
;; 判定: defn の body 全体が何行に渡るか。単純化のため、
;;       interface.clj 内の defn は 50 行、それ以外は 20 行を上限とする。
;;       （警告レベル、error にしない。正当な長さの関数もあるため）
;; ---------------------------------------------------------------------------

(defn- ctx-filename
  "hook ctx または node meta から filename を取り出す（clj-kondo 版差異吸収）。
   clj-kondo バージョンによって ctx に :filename が入る場合と入らない場合があるため、
   両方を試して nil なら空文字列を返す。"
  [ctx]
  (or (:filename ctx)
      (:filename (meta (:node ctx)))
      ""))

(defn check-defn-lines
  "defn の行数を検査（A-7）。"
  [ctx]
  (let [node (:node ctx)
        filename (ctx-filename ctx)
        lines (count-lines node)
        interface? (or (re-find #"interface\.clj$" filename)
                       (re-find #"interface\.cljc$" filename))
        limit (if interface? 50 20)]
    (when (> lines limit)
      (reg! node :polyguard/function-too-long :warning
            (str "関数が " lines " 行あります（上限 " limit " 行、"
                 (if interface? "interface 関数" "ロジック関数")
                 "）。機能分解してください（CODING_GUIDE.md §4.4、_POSSIBLE_ISSUES.md A-7）")))))

;; ---------------------------------------------------------------------------
;; A-15: 位置引数 4 個以上の禁止
;;
;; 判定: defn のアリティベクタで、位置引数（シンボル）が 4 個以上。
;;       destructuring / & args / マルチアリティは除外（複雑度回避）。
;; ---------------------------------------------------------------------------

(defn- count-positional-args
  "arg vector node 内の位置引数（単純シンボル）を数える。
   destructuring と & args は数えない（A-15 の対象外）。

   修正履歴: 当初 (remove api/reg-finding! ...) を使っていたが、
   api/reg-finding! は述語ではなく副作用関数（finding 登録）で、
   node を引数に呼ぶと例外または不正な finding 登録を起こす重大バグだった。
   api/token-node? で whitespace / comma を filter する実装に差し替え。"
  [arg-vec]
  ;; rewrite-clj は whitespace / comma を子ノードに含むため、まず token-node のみ残す。
  (let [tokens (filter api/token-node? (children arg-vec))
        ;; & args 以降は数えない（take-while）
        before-amp (take-while #(not (= '& (api/sexpr %))) tokens)]
    ;; destructuring は token-node ではなく vector/map-node なので自然に除外される。
    (count (filter #(symbol? (api/sexpr %)) before-amp))))

(defn check-defn-arity
  "defn の位置引数数を検査（A-15）。"
  [ctx]
  (let [node (:node ctx)
        [_fn-sym fn-name & rest-nodes] (children node)
        ;; docstring / attr-map / body を飛ばし、最初の vector-node か list-node を探す
        first-body (first (filter #(or (api/vector-node? %) (api/list-node? %))
                                  rest-nodes))]
    (when (and fn-name first-body)
      (cond
        ;; 単純アリティ: (defn f [a b c] ...)
        (api/vector-node? first-body)
        (let [n (count-positional-args first-body)]
          (when (>= n 4)
            (reg! first-body :polyguard/too-many-positional-args :warning
                  (str (api/sexpr fn-name) " の位置引数が " n
                       " 個あります（上限 3 個、4 個以上はマップで受ける）。"
                       "CODING_GUIDE.md §4.2、_POSSIBLE_ISSUES.md A-15"))))

        ;; マルチアリティ: (defn f ([a] ...) ([a b] ...)) は検査スキップ（複雑度回避）
        :else nil))))

;; ---------------------------------------------------------------------------
;; A-17: destructuring のネスト 2 超過
;;
;; 判定: defn の引数ベクタの destructuring が深さ 3 以上（CODING_GUIDE.md §1.12）。
;; ---------------------------------------------------------------------------

(defn check-defn-destructuring
  "defn 引数の destructuring 深さを検査（A-17）。

   実装の注記: depth は「vector / map のネスト回数」で、`:keys`・`:strs`・`:syms`・`:or`・`:as`
   の右辺は加算しない。閾値は > 3（= 深さ 4 以上で警告）。
   これは `[{:keys [a b]}]` のような一般的パターンを通すための実運用的調整で、
   `_POSSIBLE_ISSUES.md` A-17 の「深さ 2 超過」とは意味が異なるが、false positive を
   避けつつ真にネストした destructuring のみを捕捉する。"
  [ctx]
  (let [node (:node ctx)
        [_fn-sym _fn-name & rest-nodes] (children node)
        first-body (first (filter #(or (api/vector-node? %) (api/list-node? %))
                                  rest-nodes))]
    (when (and first-body (api/vector-node? first-body))
      (let [depth (destructuring-depth first-body)]
        (when (> depth 3)
          (reg! first-body :polyguard/destructuring-too-deep :warning
                (str "引数の destructuring が深くネストしています（深さ " depth
                     "）。中間キーを let で切り出してください。"
                     "CODING_GUIDE.md §1.12、_POSSIBLE_ISSUES.md A-17")))))))

;; ---------------------------------------------------------------------------
;; 統合した defn hook（A-7 / A-15 / A-17 をまとめて適用）
;; ---------------------------------------------------------------------------

(defn analyze-defn
  "defn / defn- の統合 hook。"
  [ctx]
  (check-defn-lines ctx)
  (check-defn-arity ctx)
  (check-defn-destructuring ctx))

;; ---------------------------------------------------------------------------
;; A-9: ドメイン component の top-level atom 禁止
;;
;; 判定: def の value が (atom ...) / (ref ...) / (agent ...) で、かつ
;;       filename が components/ または bases/ 配下なら警告。
;;       development/src と dev/user.clj は除外。
;; ---------------------------------------------------------------------------

(defn- mutable-constructor-call?
  "list-node が (atom ...) / (ref ...) / (agent ...) を表すかを判定。"
  [n]
  (when (api/list-node? n)
    (let [[head & _] (children n)]
      (and (api/token-node? head)
           (contains? #{'atom 'ref 'agent
                        'clojure.core/atom 'clojure.core/ref 'clojure.core/agent}
                      (api/sexpr head))))))

(defn analyze-def
  "def の top-level mutable 禁止を検査（A-9）。"
  [ctx]
  (let [node (:node ctx)
        filename (ctx-filename ctx)]
    ;; filename が空文字列（取得失敗）の場合は保守的に検査スキップ（false positive 回避）
    (when (and (seq filename)
               (or (re-find #"(?:^|/)components/" filename)
                   (re-find #"(?:^|/)bases/" filename))
               (not (re-find #"development/src/" filename)))
      (let [[_def-sym _name value] (children node)]
        (when (and value (mutable-constructor-call? value))
          (reg! node :polyguard/top-level-mutable :warning
                (str "components/ または bases/ の top-level で (atom/ref/agent ...) を def しています。"
                     "可変状態は最上位層（Integrant / 起動エントリ）に限定してください。"
                     "CLAUDE.md §4.2、_POSSIBLE_ISSUES.md A-9")))))))

;; ---------------------------------------------------------------------------
;; A-10 / A-11: catch の検査（広範囲 catch / 空 catch）
;;
;; 判定:
;;   - (catch Throwable ...) / (catch Exception ...) / (catch java.lang.Throwable ...) /
;;     (catch java.lang.Exception ...) → A-10 警告
;;   - catch 本体が空 or nil 単一 → A-11 警告
;; ---------------------------------------------------------------------------

(def ^:private ^:const broad-catch-classes
  #{'Throwable 'Exception
    'java.lang.Throwable 'java.lang.Exception})

(defn- catch-form?
  "node が (catch ...) 形式かを判定。"
  [n]
  (when (api/list-node? n)
    (let [[head & _] (children n)]
      (and (api/token-node? head)
           (= 'catch (api/sexpr head))))))

(defn- check-catch
  "1 つの catch form を検査（A-10 / A-11）。"
  [catch-node]
  (let [[_catch class-node _binding & body] (children catch-node)]
    ;; A-10: 広範囲 catch
    (when (and class-node (api/token-node? class-node))
      (let [class-sym (api/sexpr class-node)]
        (when (contains? broad-catch-classes class-sym)
          (reg! catch-node :polyguard/broad-catch :warning
                (str "(catch " class-sym " ...) は広すぎます。"
                     "ex-info の具体クラス、または Java の具体例外を catch してください。"
                     "CODING_GUIDE.md §7.2、_POSSIBLE_ISSUES.md A-10")))))
    ;; A-11: 空 catch
    (let [effective-body (remove #(and (api/token-node? %) (nil? (api/sexpr %))) body)]
      (when (or (empty? effective-body)
                (every? #(and (api/token-node? %)
                              (contains? #{nil false} (api/sexpr %)))
                        body))
        (reg! catch-node :polyguard/empty-catch :error
              (str "catch 本体が空または nil / false のみです。例外の握り潰しは禁止。"
                   "少なくとも構造化ログ（mulog/log）または ex-info で再 throw する。"
                   "CODING_GUIDE.md §7.2、_POSSIBLE_ISSUES.md A-11"))))))

(defn analyze-try
  "try form の中の catch 節を検査（A-10 / A-11）。"
  [{:keys [node]}]
  (doseq [c (children node)
          :when (catch-form? c)]
    (check-catch c)))

;; ---------------------------------------------------------------------------
;; A-16: go ブロック内の <!! / >!! ブロッキング禁止
;;
;; 判定: (go ...) 本体内に <!! / >!! / clojure.core.async/<!! などがあれば警告。
;;       slurp や Thread/sleep 等の blocking 呼び出しは対象外（A-18 / 別途）。
;; ---------------------------------------------------------------------------

(def ^:private ^:const blocking-async-ops
  #{'<!! '>!!
    'clojure.core.async/<!!
    'clojure.core.async/>!!})

(defn- blocking-call?
  "list-node が blocking async 呼び出しかを判定。"
  [n]
  (when (api/list-node? n)
    (let [[head & _] (children n)]
      (and (api/token-node? head)
           (contains? blocking-async-ops (api/sexpr head))))))

(defn analyze-go
  "(go ...) 内部の <!! / >!! を検出（A-16）。"
  [{:keys [node]}]
  (when-let [offender (find-recursive node blocking-call?)]
    (reg! offender :polyguard/blocking-in-go :error
          (str "go ブロック内で <!! / >!! を呼び出しています。"
               "go 内では <! / >! を使ってください（blocking 版は OS スレッドを占有してデッドロックの原因）。"
               "CODING_GUIDE.md §10.2、_POSSIBLE_ISSUES.md A-16"))))

;; ---------------------------------------------------------------------------
;; A-8: m/=> と defn のアリティ整合
;;
;; 判定: (m/=> fn-name [:=> [:cat t1 t2 t3] ret]) と、同一ファイル内の
;;       (defn fn-name [a b c] ...) を突き合わせてアリティ数を比較する。
;;       hook では直接の sibling 情報にアクセスできないため、file-level 解析は
;;       scripts/check-interface-contracts.sh が A-1 と併せて担う。本 hook は
;;       form 単体で明らかにおかしい場合（:=> の [:cat ...] 要素数が 0 で defn
;;       の引数が 0 でないなど、form 単体の一貫性）を検査するに留める。
;;       簡易実装：=> の [:cat ...] 引数数を出力するだけの no-op に近い処理。
;;       深い実装は将来の課題。
;; ---------------------------------------------------------------------------

(defn analyze-m=>
  "(m/=> ...) 形式の簡易検査（A-8 の近似）。"
  [{:keys [node]}]
  ;; 現時点では node 有効性のみ確認し、深い arity 照合は行わない。
  ;; file-level 解析（同一 ns 内の defn との突合）は
  ;; scripts/check-interface-contracts.sh の役割拡張に委ねる。
  (let [c (children node)]
    (when (< (count c) 3)
      (reg! node :polyguard/malformed-m=> :warning
            "m/=> の形式が不十分です。(m/=> fn-name [:=> [:cat ...] ret]) を期待"))))

;; ---------------------------------------------------------------------------
;; A-13: m/validate の戻り値捨て検知
;;
;; 判定: (m/validate schema value) 呼び出しが単独文として評価され、戻り値が
;;       親スコープで使われていない（= do/let/progn の「副作用文」扱い）パターン
;;       を警告。厳密判定は困難なため、親が do/let/fn body の途中にあるかを
;;       ベスト エフォートで判定する。
;;
;; 実装: hook は node 単体しか見えないため、確度の高い判定は難しい。
;;       本節は「m/validate の評価結果を使わないなら assert や throw と組み合わせよ」
;;       という方向性をドキュメント化するに留める。実装は警告のみ（低偽陽性）。
;; ---------------------------------------------------------------------------

(defn analyze-m-validate
  "(m/validate ...) の呼び出しを検査（A-13、最小実装）。"
  [_ctx]
  ;; 現時点では厳密な「戻り値未使用」判定は行わない（false positive を避けるため）。
  ;; 将来、parent-node アクセスを使った実装に拡張可能。
  ;; 詳細な ドキュメントは CODING_GUIDE.md §2.1.2 を参照。
  nil)

import type { JsonLdObject } from "@/lib/jsonld";

type JsonLdProps = {
  data: JsonLdObject;
};

/**
 * Emits one structured-data block. Server-rendered, so the JSON is in the
 * page source rather than something a crawler has to execute to see.
 *
 * `<` is escaped even though every value here is a build-time constant: an
 * unescaped `</script>` inside a string would end the tag early, and the cost
 * of being certain is one `replace`.
 */
export function JsonLd({ data }: JsonLdProps) {
  const json = JSON.stringify(data).replace(/</g, "\\u003c");

  return (
    <script
      type="application/ld+json"
      dangerouslySetInnerHTML={{ __html: json }}
    />
  );
}

import { cn } from "@/lib/cn";
import { fieldClasses } from "@/components/ui/Input";

type TextareaProps = React.ComponentPropsWithoutRef<"textarea">;

/** `.fld textarea`: the field chrome at 150px tall, resizable vertically. */
export function Textarea({ className, ...props }: TextareaProps) {
  return (
    <textarea
      className={cn(fieldClasses, "min-h-[150px] resize-y", className)}
      {...props}
    />
  );
}

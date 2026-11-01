import { Button } from "@/components/ui/button";

export function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Pagination" className="mt-6 flex items-center justify-between gap-4">
      <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onPage(page - 1)}>
        Previous
      </Button>
      <span className="text-sm text-muted-foreground tabular-nums">
        Page {page + 1} of {totalPages}
      </span>
      <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
        Next
      </Button>
    </nav>
  );
}

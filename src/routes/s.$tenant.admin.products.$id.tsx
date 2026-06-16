import { createFileRoute, Link } from "@tanstack/react-router";
import { ProductForm } from "@/components/admin/ProductForm";
import { useTenantProducts } from "@/stores/admin";
import { Button } from "@/components/ui/button";

export const Route = createFileRoute("/s/$tenant/admin/products/$id")({
  head: ({ params }) => ({ meta: [{ title: `Edit product · ${params.tenant}` }] }),
  component: EditProduct,
});

function EditProduct() {
  const { tenant: slug, id } = Route.useParams();
  const products = useTenantProducts(slug);
  const product = products.find((p) => p.id === id);
  if (!product) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16 text-center">
        <h1 className="text-lg font-semibold">Product not found</h1>
        <Button asChild className="mt-4 rounded-full">
          <Link to="/s/$tenant/admin/products" params={{ tenant: slug }}>Back to products</Link>
        </Button>
      </div>
    );
  }
  return <ProductForm tenantSlug={slug} initial={product} mode="edit" />;
}

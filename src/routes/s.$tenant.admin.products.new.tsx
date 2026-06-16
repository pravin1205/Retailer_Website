import { createFileRoute } from "@tanstack/react-router";
import { ProductForm } from "@/components/admin/ProductForm";

export const Route = createFileRoute("/s/$tenant/admin/products/new")({
  head: ({ params }) => ({ meta: [{ title: `New product · ${params.tenant}` }] }),
  component: NewProduct,
});

function NewProduct() {
  const { tenant: slug } = Route.useParams();
  return <ProductForm tenantSlug={slug} mode="create" />;
}
